uniform int nodeLightCount;
uniform int nodeLightPass;
uniform int nodeLightIndex;
uniform int nodeLightResolved;
uniform vec4 nodePassViewport;
uniform vec2 nodeOriginalSize;
uniform float nodeLightTime;
uniform vec3 nodeNoiseOrigin;
uniform vec3 nodeEyeOffset;
uniform vec4 nodeLights[96];
uniform sampler2D nodeLightSurfaceAtlas;
uniform sampler2D nodeLightVolumeAtlas;
uniform sampler2D nodeLightCookies;
#ifndef NODE_LIGHT_NO_ENTITY_SHADOWS
uniform sampler2D nodeEntityBvh;
uniform ivec3 nodeEntityRange;
uniform int nodeEntityBoundsCount;
uniform vec4 nodeEntityBounds[32];
#endif
uniform vec4 nodeSurfaceRects[8];
#ifdef NODE_LIGHT_PIXEL_DIAGNOSTICS
uniform int nodeVolumeProbeMode;
int nodeVolumeProbeFlags=0;
bool nodeVolumeProbeActive() {return nodeVolumeProbeMode>0 && nodeVolumeProbeMode<16;}
void nodeVolumeProbeRecord(int flags) {if(nodeVolumeProbeActive())nodeVolumeProbeFlags|=flags;}
bool nodeVolumeCostSkip(int mode) {return nodeVolumeProbeMode==mode;}
#else
bool nodeVolumeProbeActive() {return false;}
void nodeVolumeProbeRecord(int flags) {}
bool nodeVolumeCostSkip(int mode) {return false;}
#endif
// Tile-local bounds of all non-air cells. Min.w records transparent material
// presence; Max.w marks valid metadata (zero keeps the original full traversal).
uniform vec4 geometry_node_blockOccupiedMin[8];
uniform vec4 geometry_node_blockOccupiedMax[8];

// Coarse bytes retain the non-air count in bits 0..4. Version 3 adds transparent
// presence (bit 5) and fully opaque (bit 6), including conservative unknown cells.
// Version 4 adds non-empty 16x1x16 parent bricks (bit 7) in the same atlas byte.
int nodeWorldBrickMaterial(int field,ivec3 brick) {
    if(geometry_node_blockOccupiedMax[field].w<1.5)return 32;
    int address=brick.x+16*(brick.y+64*brick.z);
    int material=int(texelFetch(geometry_node_blockWorld,ivec2(address&511,4096+field*32+(address>>9)),0).r*255.0+0.5);
    if(geometry_node_blockOccupiedMax[field].w<2.5 && material!=0)return 32;
    return material;
}

float nodeWorldBrickExit(ivec3 cell,ivec3 stepCell,vec3 next,vec3 delta,int edgeCell) {
    ivec3 local=cell&ivec3(edgeCell,0,edgeCell);
    ivec3 remaining=ivec3(0);
    for(int axis=0;axis<3;axis++)
        remaining[axis]=stepCell[axis]>0?(axis==1?0:edgeCell)-local[axis]:local[axis];
    vec3 edge=next+vec3(remaining)*delta;
    return min(edge.x,min(edge.y,edge.z));
}

void nodeSkipWorldBrick(float t,ivec3 stepCell,vec3 delta,inout ivec3 cell,inout vec3 next) {
    // Advance on the original DDA lattice, retaining its tie tolerance and
    // endpoint nudge rather than reconstructing a differently rounded ray.
    for(int axis=0;axis<3;axis++)if(next[axis]<=t+0.000001) {
        int crossed=1+int(floor(max(0.0,(t+0.000001-next[axis])/delta[axis])));
        cell[axis]+=crossed*stepCell[axis];next[axis]+=float(crossed)*delta[axis];
    }
}

#ifndef NODE_LIGHT_NO_ENTITY_SHADOWS
vec4 nodeEntityFetch(int address) {
    // NodeLightEntityShadows keeps the atlas width fixed at 256 texels.
    return texelFetch(nodeEntityBvh,ivec2(address&255,address>>8),0);
}

float nodeEntityVisibility(int lightIndex,vec3 receiver,vec3 source,ivec2 nodes) {
    if(nodes.x>=nodes.y)return 1.0;
    vec3 direction=receiver-source;
    float distanceToLight=length(direction);
    if(distanceToLight<=0.004)return 1.0;
    float bias=0.002/distanceToLight;
    vec3 inverseDirection=1.0/mix(direction,vec3(1.0),lessThan(abs(direction),vec3(1e-12)));
    int node=nodes.x;
    // Threaded traversal visits each node at most once. Keep the bound dynamic so
    // drivers do not attempt to unroll thousands of visits at each lighting call.
    int visitLimit=min(nodes.y-nodes.x,16384);
    for(int visit=0;visit<visitLimit;visit++) {
        if(node<0 || node>=nodes.y)break;
        vec4 lower=nodeEntityFetch(node*2),upper=nodeEntityFetch(node*2+1);
        if(!geometry_node_lightBox(source,direction,inverseDirection,lower.xyz,upper.xyz,bias)) {
            node=int(lower.w);continue;
        }
        int metadata=floatBitsToInt(upper.w)&0x0fffffff;
        int count=metadata&15;
        if(count==0) {node++;continue;}
        int first=metadata>>4;
        for(int triangle=0;triangle<count;triangle++) {
            int address=nodeEntityRange.y+(first+triangle)*3;
            vec4 second=nodeEntityFetch(address+1);
            if((int(second.w)&(1<<lightIndex))!=0)continue;
            if(geometry_node_lightTriangleHit(source,direction,nodeEntityFetch(address).xyz,
                    second.xyz,nodeEntityFetch(address+2).xyz,bias)>=0.0)return 0.0;
        }
        node=int(lower.w);
    }
    return 1.0;
}
float nodeEntityVisibility(int lightIndex,vec3 receiver,vec3 source) {
    return nodeEntityVisibility(lightIndex,receiver,source,ivec2(0,nodeEntityRange.x));
}

// Every emitter-to-sample segment lies within triangle(source, first, last)
// expanded by the emitter radius. Reject only entities outside that entire sweep.
bool nodeVolumeHasEntities(vec3 source,vec3 first,vec3 last,float radius) {
    if(nodeEntityRange.x<=0)return false;
    if(nodeEntityBoundsCount<=0)return true;
    vec3 lower=min(source,min(first,last)),upper=max(source,max(first,last));
    vec3 a=first-source,b=last-source,n=cross(a,b);
    float nLength=length(n);
    vec3 edge0=cross(n,a),edge1=cross(n,last-first),edge2=cross(n,-b);
    vec3 edgeLength=vec3(length(edge0),length(edge1),length(edge2));
    // Near-collinear sweeps retain the AABB test and avoid unstable plane tests.
    bool planes=nLength>0.00001*max(length(a)*length(b),1.0);
    for(int i=0;i<min(nodeEntityBoundsCount,32);i++) {
        vec4 sphere=nodeEntityBounds[i];
        float reach=sphere.w+radius+0.002;
        vec3 gap=sphere.xyz-clamp(sphere.xyz,lower,upper);
        if(dot(gap,gap)>reach*reach)continue;
        if(planes) {
            vec3 delta=sphere.xyz-source;
            if(abs(dot(n,delta))>reach*nLength)continue;
            if(dot(edge0,delta)< -reach*edgeLength.x)continue;
            if(dot(edge1,sphere.xyz-first)< -reach*edgeLength.y)continue;
            if(dot(edge2,sphere.xyz-last)< -reach*edgeLength.z)continue;
        }
        return true;
    }
    return false;
}
ivec2 nodeVolumeEntityRange(vec3 source,vec3 first,vec3 last,float radius) {
    if(!nodeVolumeHasEntities(source,first,last,radius))return ivec2(0);
    // A tiny tree costs less to traverse than to preselect for a short march.
    if(nodeEntityRange.x<=7)return ivec2(0,nodeEntityRange.x);
    // Every shadow ray belongs to this swept box. Descend only when all possible
    // hits belong to one child, then share the reduced subtree across march samples.
    vec3 lower=min(source,min(first,last))-vec3(radius+0.004);
    vec3 upper=max(source,max(first,last))+vec3(radius+0.004);
    int root=0,end=nodeEntityRange.x;
    int metadata=floatBitsToInt(nodeEntityFetch(1).w)&0x0fffffff;
    for(int level=0;level<32;level++) {
        if((metadata&15)!=0)break;
        int left=root+1,right=metadata>>4;
        vec4 l0=nodeEntityFetch(left*2),l1=nodeEntityFetch(left*2+1);
        vec4 r0=nodeEntityFetch(right*2),r1=nodeEntityFetch(right*2+1);
        bool l=all(lessThanEqual(l0.xyz,upper)) && all(greaterThanEqual(l1.xyz,lower));
        bool r=all(lessThanEqual(r0.xyz,upper)) && all(greaterThanEqual(r1.xyz,lower));
        if(l && r)break;
        if(!l && !r)return ivec2(0);
        root=l?left:right;
        int escape=int(l?l0.w:r0.w);
        end=escape<0?nodeEntityRange.x:escape;
        metadata=floatBitsToInt(l?l1.w:r1.w)&0x0fffffff;
    }
    return ivec2(root,end);
}
#else
float nodeEntityVisibility(int lightIndex,vec3 receiver,vec3 source) {return 1.0;}
float nodeEntityVisibility(int lightIndex,vec3 receiver,vec3 source,ivec2 nodes) {return 1.0;}
bool nodeVolumeHasEntities(vec3 source,vec3 first,vec3 last,float radius) {return false;}
ivec2 nodeVolumeEntityRange(vec3 source,vec3 first,vec3 last,float radius) {return ivec2(0);}
#endif

float nodeHash(vec3 p) {
    p=mod(p,4096.0);
    p=fract(p*0.1031); p+=dot(p,p.yzx+33.33); return fract((p.x+p.y)*p.z);
}
float nodeNoise(vec3 p) {
    vec3 a=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(nodeHash(a),nodeHash(a+vec3(1,0,0)),f.x),
                   mix(nodeHash(a+vec3(0,1,0)),nodeHash(a+vec3(1,1,0)),f.x),f.y),
               mix(mix(nodeHash(a+vec3(0,0,1)),nodeHash(a+vec3(1,0,1)),f.x),
                   mix(nodeHash(a+vec3(0,1,1)),nodeHash(a+vec3(1,1,1)),f.x),f.y),f.z);
}

// Matches NodeLightWorldMaterial: dye+1, clear=17, pane=32, water=64, opaque=255.
// Pane absorption uses 1/8 block equivalent thickness, not a shape intersection.
vec3 nodeWorldExtinction(int material) {
    vec3 extinction=(material&64)!=0?vec3(0.12,0.045,0.025):vec3(0.0);
    int glass=material&31;
    if(glass==0)return extinction;
    float thickness=(material&32)!=0?0.125:1.0;
    if(glass==17)return extinction+vec3(0.040822*thickness);
    const vec3 dyes[16]=vec3[16](
        vec3(249,255,254),vec3(249,128,29),vec3(199,78,189),vec3(58,179,218),
        vec3(254,216,61),vec3(128,199,31),vec3(243,139,170),vec3(71,79,82),
        vec3(157,157,151),vec3(22,156,156),vec3(137,50,184),vec3(60,68,170),
        vec3(131,84,50),vec3(94,124,22),vec3(176,46,38),vec3(29,29,33));
    if(glass>16)return extinction+vec3(8.0);
    return extinction+thickness*(vec3(0.040822)+1.2*(1.0-dyes[glass-1]/255.0));
}

// A segment crosses at most ceil(abs(delta)) planes per axis, plus its starting cell.
// Extra cells cover endpoint nudges/roundoff. Retain the old cap, but keep the bound
// dynamic: constant 196-visit loops nested in volume/shadow sampling inflate driver compilation.
int nodeWorldVisitLimit(vec3 segment) {
    vec3 crossings=ceil(abs(segment));
    return min(196,4+int(crossings.x+crossings.y+crossings.z));
}

int nodeWorldTileLimit() {
    int count=0;
    for(int field=0;field<8;field++)
        if(geometry_node_blockOrigins[field].w>=0.5)count=field+1;
    return count;
}

// Every emitter-to-sample segment is inside this sweep. A single ready tile
// must cover it completely before known-empty bounds can bypass world tracing.
bool nodeVolumeWorldClear(vec3 source,vec3 first,vec3 last,float radius,int tileLimit,out int worldTile) {
    worldTile=-1;
    if(geometry_node_placedReady==0)return false;
    // Only the emitter moves within its radius; receiver endpoints stay fixed.
    // The convex hull of these bounds encloses every emitter-to-sample ray.
    vec3 extent=vec3(max(radius,0.0)),padding=vec3(0.002);
    vec3 lower=min(source-extent,min(first,last))-padding;
    vec3 upper=max(source+extent,max(first,last))+padding;
    for(int field=0;field<tileLimit;field++) {
        if(geometry_node_blockOrigins[field].w<0.5)continue;
        vec3 origin=geometry_node_blockOrigins[field].xyz;
        if(any(lessThan(upper,origin)) || any(greaterThanEqual(lower,origin+64.0)))continue;
        // Preserve first-tile selection when ready tiles overlap or refresh at
        // different times. Uncertain coverage keeps the original per-ray choice.
        if(any(lessThan(lower,origin)) || any(greaterThanEqual(upper,origin+64.0)))return false;
        worldTile=field;
        if(geometry_node_blockOccupiedMax[field].w<0.5)return false;
        vec3 occupiedMin=geometry_node_blockOccupiedMin[field].xyz;
        vec3 occupiedMax=geometry_node_blockOccupiedMax[field].xyz;
        if(any(greaterThanEqual(occupiedMin,occupiedMax)))return true;
        return any(lessThan(upper,origin+occupiedMin)) || any(greaterThan(lower,origin+occupiedMax));
    }
    return false;
}

bool nodeOccupiedInterval(int field,vec3 source,vec3 direction,inout float start,inout float finish) {
    if(geometry_node_blockOccupiedMax[field].w<0.5)return true;
    vec3 lo=geometry_node_blockOccupiedMin[field].xyz;
    vec3 hi=geometry_node_blockOccupiedMax[field].xyz;
    if(any(greaterThanEqual(lo,hi)))return false;
    source-=geometry_node_blockOrigins[field].xyz;
    for(int axis=0;axis<3;axis++) {
        if(abs(direction[axis])<1e-7) {
            if(source[axis]<lo[axis] || source[axis]>=hi[axis])return false;
        } else {
            float a=(lo[axis]-source[axis])/direction[axis];
            float b=(hi[axis]-source[axis])/direction[axis];
            start=max(start,min(a,b));finish=min(finish,max(a,b));
        }
    }
    return finish>start;
}

// Clip the segment to each ready tile. Uncovered segments are dark, never silently leak light.
// Accumulate optical depth during the existing DDA; a single exponential handles the whole ray.
vec3 nodeWorldTransmission(vec3 receiver,vec3 source,int tileLimit,int worldTile) {
    if(geometry_node_placedReady==0)return vec3(0.0);
    vec3 d=receiver-source;
    float rayLength=length(d);
    vec3 opticalDepth=vec3(0.0);
    float covered=0.0;
    for(int stepTile=0;stepTile<tileLimit;stepTile++) {
        float farthest=worldTile>=0?1.0:covered; int selected=worldTile; float start=0.0;
        // A sweep-wide proof preserves the same tile choice for every sample.
        for(int field=0;worldTile<0 && field<tileLimit;field++) {
            if(geometry_node_blockOrigins[field].w<0.5) continue;
            vec3 lo=geometry_node_blockOrigins[field].xyz;
            // Most emitter rays stay in one tile. Containment proves the same
            // [0,1] slab interval without six per-axis divisions per sample.
            if(all(greaterThanEqual(source,lo)) && all(lessThan(source,lo+64.0))
                    && all(greaterThanEqual(receiver,lo)) && all(lessThan(receiver,lo+64.0))) {
                farthest=1.0;selected=field;start=covered;break;
            }
            float a=0.0,b=1.0;
            for(int axis=0;axis<3;axis++) {
                if(abs(d[axis])<1e-7) {
                    if(source[axis]<lo[axis] || source[axis]>=lo[axis]+64.0) b=-1.0;
                } else {
                    float x=(lo[axis]-source[axis])/d[axis], y=(lo[axis]+64.0-source[axis])/d[axis];
                    a=max(a,min(x,y)); b=min(b,max(x,y));
                }
            }
            if(a<=covered+0.00001 && b>farthest) {farthest=b;selected=field;start=max(a,covered);}
            if(farthest>=1.0)break;
        }
        if(selected<0) return vec3(0.0);
        float occupiedEnd=farthest;
        if(!nodeOccupiedInterval(selected,source,d,start,occupiedEnd)) {
            covered=farthest;
            if(covered>=0.99999)return exp(-opticalDepth);
            continue;
        }
        vec3 lo=geometry_node_blockOrigins[selected].xyz;
        vec3 p=clamp(source+d*(start+0.000001)-lo,vec3(0.0001),vec3(63.9999));
        ivec3 cell=ivec3(floor(p)),stepCell=ivec3(sign(d));
        vec3 delta=vec3(1e20),next=vec3(1e20);
        for(int axis=0;axis<3;axis++) if(abs(d[axis])>1e-7) {
            delta[axis]=abs(1.0/d[axis]);
            float edge=float(cell[axis])+(stepCell[axis]>0?1.0:0.0);
            next[axis]=start+(edge-p[axis])/d[axis];
        }
        float cellStart=start;
        int visitLimit=nodeWorldVisitLimit(d*(occupiedEnd-start));
        ivec3 checkedBrick=ivec3(-1);int brickMaterial=32;
        for(int visit=0;visit<visitLimit;visit++) {
            ivec3 brick=ivec3(cell.x>>2,cell.y,cell.z>>2);
            if(any(notEqual(brick,checkedBrick))) {
                checkedBrick=brick;brickMaterial=nodeWorldBrickMaterial(selected,brick);
            }
            if((brickMaterial&64)!=0)return vec3(0.0);
            if((brickMaterial&63)==0) {
                int edgeCell=geometry_node_blockOccupiedMax[selected].w>=3.5 && (brickMaterial&128)==0?15:3;
                float t=nodeWorldBrickExit(cell,stepCell,next,delta,edgeCell);
                if(t>=occupiedEnd-0.000001)break;
                cellStart=t;nodeSkipWorldBrick(t,stepCell,delta,cell,next);
                if(any(lessThan(cell,ivec3(0))) || any(greaterThanEqual(cell,ivec3(64))))break;
                continue;
            }
            // The receiver is already biased away from its surface; test every traversed cell.
            int address=cell.x+64*(cell.y+64*cell.z);
            int material=int(texelFetch(geometry_node_blockWorld,ivec2(address&511,(address>>9)+selected*512),0).r*255.0+0.5);
            if(material>=128) return vec3(0.0);
            float t=min(next.x,min(next.y,next.z));
            if(material!=0)opticalDepth+=nodeWorldExtinction(material)*(max(0.0,min(t,occupiedEnd)-cellStart)*rayLength);
            if(t>=occupiedEnd-0.000001) break;
            cellStart=t;
            for(int axis=0;axis<3;axis++) if(next[axis]<=t+0.000001) {cell[axis]+=stepCell[axis];next[axis]+=delta[axis];}
            if(any(lessThan(cell,ivec3(0))) || any(greaterThanEqual(cell,ivec3(64)))) break;
        }
        covered=farthest;
        if(covered>=0.99999) return exp(-opticalDepth);
    }
    return vec3(0.0);
}

vec3 nodeWorldTransmission(vec3 receiver,vec3 source,int tileLimit) {
    return nodeWorldTransmission(receiver,source,tileLimit,-1);
}

vec3 nodeWorldTransmission(vec3 receiver,vec3 source) {
    return nodeWorldTransmission(receiver,source,nodeWorldTileLimit());
}

float nodeWorldVisibility(vec3 receiver,vec3 source) {
    vec3 transmission=nodeWorldTransmission(receiver,source);
    return max(transmission.r,max(transmission.g,transmission.b));
}

// Camera rays already have opaque scene-depth clipping. Accumulate only known
// transparent materials; missing tiles and unscanned cells must not hide the view.
vec3 nodeCameraOpticalDepth(vec3 source,vec3 receiver,int tileLimit) {
    if(geometry_node_placedReady==0)return vec3(0.0);
    vec3 d=receiver-source;
    float rayLength=length(d),covered=0.0;
    vec3 opticalDepth=vec3(0.0);
    for(int tileVisit=0;tileVisit<tileLimit;tileVisit++) {
        int selected=-1;float start=2.0,finish=0.0;
        for(int field=0;field<tileLimit;field++) {
            if(geometry_node_blockOrigins[field].w<0.5)continue;
            vec3 lo=geometry_node_blockOrigins[field].xyz;
            float a=0.0,b=1.0;
            for(int axis=0;axis<3;axis++) {
                if(abs(d[axis])<1e-7) {
                    if(source[axis]<lo[axis] || source[axis]>=lo[axis]+64.0)b=-1.0;
                } else {
                    float x=(lo[axis]-source[axis])/d[axis],y=(lo[axis]+64.0-source[axis])/d[axis];
                    a=max(a,min(x,y));b=min(b,max(x,y));
                }
            }
            a=max(a,covered);
            if(b>a && (a<start || (a==start && b>finish))) {selected=field;start=a;finish=b;}
        }
        if(selected<0)break;
        if(geometry_node_blockOccupiedMax[selected].w>=0.5 && geometry_node_blockOccupiedMin[selected].w<0.5) {
            covered=finish;
            if(covered>=0.99999)break;
            continue;
        }
        vec3 lo=geometry_node_blockOrigins[selected].xyz;
        vec3 p=clamp(source+d*start-lo,vec3(0.0001),vec3(63.9999));
        ivec3 cell=ivec3(floor(p)),stepCell=ivec3(sign(d));
        vec3 delta=vec3(1e20),next=vec3(1e20);
        for(int axis=0;axis<3;axis++)if(abs(d[axis])>1e-7) {
            delta[axis]=abs(1.0/d[axis]);
            float edge=float(cell[axis])+(stepCell[axis]>0?1.0:0.0);
            next[axis]=start+(edge-p[axis])/d[axis];
        }
        float cellStart=start;
        int visitLimit=nodeWorldVisitLimit(d*(finish-start));
        ivec3 checkedBrick=ivec3(-1);int brickMaterial=32;
        for(int visit=0;visit<visitLimit;visit++) {
            ivec3 brick=ivec3(cell.x>>2,cell.y,cell.z>>2);
            if(any(notEqual(brick,checkedBrick))) {
                checkedBrick=brick;brickMaterial=nodeWorldBrickMaterial(selected,brick);
            }
            // Opaque scene depth already bounds camera rays; only transparent
            // materials contribute here, even in partly unknown world snapshots.
            if((brickMaterial&32)==0) {
                int edgeCell=geometry_node_blockOccupiedMax[selected].w>=3.5 && (brickMaterial&128)==0?15:3;
                float t=nodeWorldBrickExit(cell,stepCell,next,delta,edgeCell);
                if(t>=finish-0.000001)break;
                cellStart=t;nodeSkipWorldBrick(t,stepCell,delta,cell,next);
                if(any(lessThan(cell,ivec3(0))) || any(greaterThanEqual(cell,ivec3(64))))break;
                continue;
            }
            int address=cell.x+64*(cell.y+64*cell.z);
            int material=int(texelFetch(geometry_node_blockWorld,ivec2(address&511,(address>>9)+selected*512),0).r*255.0+0.5);
            float t=min(next.x,min(next.y,next.z));
            if(material>0 && material<128)
                opticalDepth+=nodeWorldExtinction(material)*(max(0.0,min(t,finish)-cellStart)*rayLength);
            if(t>=finish-0.000001)break;
            cellStart=t;
            for(int axis=0;axis<3;axis++)if(next[axis]<=t+0.000001) {cell[axis]+=stepCell[axis];next[axis]+=delta[axis];}
            if(any(lessThan(cell,ivec3(0))) || any(greaterThanEqual(cell,ivec3(64))))break;
        }
        covered=finish;
        if(covered>=0.99999)break;
    }
    return opticalDepth;
}

vec3 nodeCameraOpticalDepth(vec3 source,vec3 receiver) {
    return nodeCameraOpticalDepth(source,receiver,nodeWorldTileLimit());
}

// Fixed-power quadrature: radius changes the emitting surface, not total strength.
// Four samples are the minimum finite-surface approximation, even without shadows.
int nodeEmitterSamples(int index) {
    return nodeLights[index*12+5].y<=0.0?1:clamp(int(nodeLights[index*12+5].z),4,16);
}

bool nodeAreaEmitter(int index) {return nodeLights[index*12+7].z>0.0;}

vec3 nodeAreaEdge(vec3 a,vec3 b) {
    vec3 edge=cross(a,b);float magnitude=length(edge);
    return magnitude>1e-8?edge*(atan(magnitude,dot(a,b))/magnitude):vec3(0.0);
}
float nodeAreaCorner(float x,float y,float z) {
    return atan(x*y,z*sqrt(x*x+y*y+z*z));
}

// Equal-area rectangular patches cover the complete emitter for every sample count.
// Integrate each patch's angular extent instead of treating it as a singular point.
void nodeAreaEmitterSample(int index,vec3 p,int sampleIndex,int samples,bool surface,
                           out vec3 source,out float weight) {
    int b=index*12;
    vec3 center=nodeLights[b].xyz,axis=nodeLights[b+1].xyz,right=nodeLights[b+3].xyz,up=nodeLights[b+4].xyz;
    source=center;weight=0.0;
    vec3 local=p-center;
    float axial=dot(local,axis);
    if(axial<=0.0000001)return;
    vec2 size=2.0*vec2(nodeLights[b+7].z,nodeLights[b+10].w);
    int rows=int(sqrt(float(samples))),base=samples/rows,extra=samples%rows;
    int first=0,columns=base;
    for(int r=0;r<4;r++) {
        columns=base+(r<extra?1:0);
        if(sampleIndex<first+columns)break;
        first+=columns;
    }
    vec2 lower=vec2(float(sampleIndex-first)/float(columns),float(first)/float(samples))*size-size*0.5;
    vec2 upper=lower+vec2(size.x/float(columns),size.y*float(columns)/float(samples));
    vec2 midpoint=(lower+upper)*0.5;
    source=center+right*midpoint.x+up*midpoint.y;
    vec3 delta=source-p;
    float distanceSquared=dot(delta,delta),patchSquare=dot(upper-lower,upper-lower);
    float solidAngle;vec3 moment;
    if(distanceSquared>100.0*patchSquare) {
        // Stable far-field limit avoids cancellation for tiny, distant rectangles.
        solidAngle=(size.x*size.y/float(samples))*axial/(distanceSquared*sqrt(distanceSquared));
        moment=delta*inversesqrt(distanceSquared)*solidAngle;
    } else {
        vec2 projected=vec2(dot(local,right),dot(local,up));
        vec2 lo=lower-projected,hi=upper-projected;
        vec3 a=normalize(right*lo.x+up*lo.y-axis*axial);
        vec3 c=normalize(right*hi.x+up*hi.y-axis*axial);
        vec3 d=normalize(right*lo.x+up*hi.y-axis*axial);
        vec3 e=normalize(right*hi.x+up*lo.y-axis*axial);
        moment=-0.5*(nodeAreaEdge(a,e)+nodeAreaEdge(e,c)+nodeAreaEdge(c,d)+nodeAreaEdge(d,a));
        solidAngle=surface?0.0:max(0.0,nodeAreaCorner(hi.x,hi.y,axial)-nodeAreaCorner(lo.x,hi.y,axial)
                -nodeAreaCorner(hi.x,lo.y,axial)+nodeAreaCorner(lo.x,lo.y,axial));
        // Trace toward the angular centroid on the actual emitting plane.
        float toward=-dot(moment,axis);
        if(toward>1e-12)source=p+moment*(axial/toward);
    }
    weight=(surface?length(moment):solidAngle)*float(samples)/(size.x*size.y);
}

float nodeDiskSamplingDomain(int index,vec3 p,out vec3 center) {
    int b=index*12;
    center=nodeLights[b].xyz;
    float radius=nodeLights[b+5].y;
    if(radius<=0.0)return 1.0;
    float axial=dot(p-center,nodeLights[b+1].xyz);
    if(axial<=0.0)return 0.0;
    vec3 projected=p-nodeLights[b+1].xyz*axial;
    vec3 lateral=projected-center;
    float coneRadius=axial*nodeLights[b+4].w;
    if(length(lateral)>=radius+coneRadius)return 0.0;
    // A weighted sum of the two circle inequalities encloses their intersection.
    // Blend continuously to avoid changing quadrature abruptly when their sizes cross.
    float blend=1.0-smoothstep(0.75,1.25,coneRadius/radius);
    float square=mix(radius*radius,coneRadius*coneRadius,blend)-blend*(1.0-blend)*dot(lateral,lateral);
    center=mix(center,projected,blend);
    return max(square,0.0)/(radius*radius);
}

struct NodeEmitterDomain {
    vec3 center;
    vec3 axis;
    vec3 right;
    vec3 up;
    vec4 sphere;
    float ratio;
};

// The visible emitting domain depends on the receiver, never the sample index.
NodeEmitterDomain nodeEmitterDomain(int index,vec3 p) {
    int b=index*12;
    NodeEmitterDomain domain;
    domain.center=nodeLights[b].xyz;
    domain.axis=vec3(0.0);domain.right=vec3(0.0);domain.up=vec3(0.0);
    domain.sphere=vec4(0.0);domain.ratio=1.0;
    float radius=nodeLights[b+5].y;
    if(radius<=0.0 || nodeAreaEmitter(index))return domain;
    if(nodeLights[b+1].w>=-1.0) {
        domain.ratio=nodeDiskSamplingDomain(index,p,domain.center);
        return domain;
    }
    vec3 delta=domain.center-p;
    float distanceSquared=dot(delta,delta);
    if(distanceSquared<=radius*radius) {domain.ratio=0.0;return domain;}
    float distanceToCenter=sqrt(distanceSquared);
    domain.axis=delta/distanceToCenter;
    domain.right=normalize(cross(abs(domain.axis.y)<0.99?vec3(0,1,0):vec3(1,0,0),domain.axis));
    domain.up=cross(domain.axis,domain.right);
    float cosMax=sqrt(max(1.0-radius*radius/distanceSquared,0.0));
    float oneMinusCos=radius*radius/(distanceSquared*(1.0+cosMax));
    domain.sphere=vec4(distanceSquared,distanceToCenter,cosMax,oneMinusCos);
    return domain;
}

void nodeEmitterSample(int index,vec3 p,int sampleIndex,int samples,NodeEmitterDomain domain,
                       out vec3 source,out float weight) {
    int b=index*12;
    if(nodeAreaEmitter(index)) {nodeAreaEmitterSample(index,p,sampleIndex,samples,true,source,weight);return;}
    source=nodeLights[b].xyz;weight=1.0;
    float radius=nodeLights[b+5].y;
    if(radius<=0.0) {
        if(nodeLights[b+1].w>= -1.0) {
            vec3 delta=p-source;
            weight=max(dot(nodeLights[b+1].xyz,delta)*inversesqrt(max(dot(delta,delta),1e-12)),0.0);
        }
        return;
    }
    float u=(float(sampleIndex)+0.5)/float(samples);
    float angle=float(sampleIndex)*2.39996323;
    if(nodeLights[b+1].w>= -1.0) {
        float ratio=domain.ratio;
        if(ratio<=0.0) {weight=0.0;return;}
        float candidate=radius*sqrt(ratio);
        // Symmetric default quadrature avoids moving the apparent beam off its axis.
        vec2 sampleOffset;
        if(samples==4)sampleOffset=vec2(sampleIndex==0 || sampleIndex==3?0.5:-0.5,sampleIndex<2?0.5:-0.5);
        else sampleOffset=sqrt(u)*vec2(cos(angle),sin(angle));
        source=domain.center+candidate*(sampleOffset.x*nodeLights[b+3].xyz+sampleOffset.y*nodeLights[b+4].xyz);
        vec3 offset=source-nodeLights[b].xyz;
        if(dot(offset,offset)>radius*radius) {weight=0.0;return;}
        vec3 delta=p-source;
        weight=ratio*max(dot(nodeLights[b+1].xyz,delta)*inversesqrt(max(dot(delta,delta),1e-12)),0.0);
        return;
    }
    // Uniform solid-angle samples hit the visible sphere, not a camera-facing disk.
    if(domain.ratio<=0.0) {weight=0.0;return;}
    float distanceSquared=domain.sphere.x,distanceToCenter=domain.sphere.y;
    float cosMax=domain.sphere.z,oneMinusCos=domain.sphere.w;
    float cosine=1.0-u*oneMinusCos;
    float sine=sqrt(max(u*oneMinusCos*(2.0-u*oneMinusCos),0.0));
    vec3 ray=domain.axis*cosine+(domain.right*cos(angle)+domain.up*sin(angle))*sine;
    float along=distanceToCenter*cosine;
    float root=sqrt(max(radius*radius-distanceSquared*sine*sine,0.0));
    float hit=(distanceSquared-radius*radius)/max(along+root,1e-12);
    source=p+ray*hit;
    // Normalize solid angle against projected sphere area; center inverse-square
    // falloff below gives finite irradiance even immediately outside the sphere.
    weight=2.0/(1.0+cosMax);
}

float nodeRangeAttenuation(int index,float centerDistanceSquared) {
    float range=nodeLights[index*12].w;
    float rangeRatioSquared=centerDistanceSquared/(range*range);
    float falloff=max(1.0-rangeRatioSquared*rangeRatioSquared,0.0);
    return falloff*falloff;
}

vec3 nodeSampleRadiancePrepared(int index,vec3 p,vec3 source,float centerDistanceSquared,float attenuation) {
    int b=index*12;
    vec3 delta=p-source;
    float distanceSquared=dot(delta,delta),rangeSquared=nodeLights[b].w*nodeLights[b].w;
    if(centerDistanceSquared>=rangeSquared) return vec3(0.0);
    vec3 direction=delta*inversesqrt(max(distanceSquared,1e-8));
    float cone=dot(direction,nodeLights[b+1].xyz);
    float outer=nodeLights[b+1].w,inner=nodeLights[b+3].w;
    bool point=outer< -1.0;
    if(!point && cone<=outer) return vec3(0.0);
    float edge=!point && inner>outer+0.00001?smoothstep(outer,inner,cone):1.0;
    // Keep the authored range centered on the light and within the world snapshot.
    if(!nodeAreaEmitter(index))attenuation/=max(point?centerDistanceSquared:distanceSquared,1e-8);
    vec3 color=nodeLights[b+2].rgb;
    if(!point && !nodeAreaEmitter(index) && nodeLights[b+8].x>0.5) {
        float axial=max(dot(delta,nodeLights[b+1].xyz),0.0001);
        vec2 uv=vec2(dot(delta,nodeLights[b+3].xyz),dot(delta,nodeLights[b+4].xyz))/(2.0*axial*nodeLights[b+4].w);
        float angle=nodeLights[b+8].y,c=cos(angle),s=sin(angle);
        uv=mat2(c,-s,s,c)*uv/nodeLights[b+8].z;
        float aspect=nodeLights[b+11].w;
        uv*=vec2(max(aspect,1.0),max(1.0/aspect,1.0));
        uv+=0.5;
        if(any(lessThan(uv,vec2(0))) || any(greaterThan(uv,vec2(1)))) return vec3(0.0);
        vec2 cookieSize=vec2(textureSize(nodeLightCookies,0));
        uv=clamp(uv,vec2(0.5)/vec2(cookieSize.x,cookieSize.y/8.0),1.0-vec2(0.5)/vec2(cookieSize.x,cookieSize.y/8.0));
        vec4 image=textureLod(nodeLightCookies,vec2(uv.x,(float(index)+1.0-uv.y)/8.0),0.0);
        vec3 projected=nodeLights[b+8].w>0.5?pow(image.rgb,vec3(2.2)):vec3(dot(image.rgb,vec3(0.2126,0.7152,0.0722)));
        color*=projected*image.a;
    }
    return color*(nodeLights[b+2].w*edge*attenuation);
}

vec3 nodeSampleTransmission(int index,vec3 p,vec3 source,ivec2 entityNodes,int tileLimit,bool worldClear,int worldTile,int modelMask) {
    int b=index*12;
    if(nodeLights[b+5].x<0.5)return vec3(1.0);
    if(nodeEntityVisibility(index,p,source,entityNodes)<=0.0)return vec3(0.0);
    vec3 transmission=worldClear?vec3(1.0):nodeWorldTransmission(p,source,tileLimit,worldTile);
    if(max(transmission.r,max(transmission.g,transmission.b))<=0.0)return vec3(0.0);
    return transmission*nodeModelVisibilityMasked(p,source,modelMask);
}
vec3 nodeSampleTransmission(int index,vec3 p,vec3 source,bool entities) {
    if(nodeLights[index*12+5].x<0.5)return vec3(1.0);
    if(entities && nodeEntityVisibility(index,p,source)<=0.0)return vec3(0.0);
    vec3 transmission=nodeWorldTransmission(p,source);
    if(max(transmission.r,max(transmission.g,transmission.b))<=0.0)return vec3(0.0);
    return transmission*nodeModelVisibility(p,source);
}
vec3 nodeSampleTransmission(int index,vec3 p,vec3 source) {
    return nodeSampleTransmission(index,p,source,true);
}

// Reject the union of all emitter samples, not merely its center cone.
bool nodeLightCanReach(int index,vec3 p) {
    int b=index*12;
    vec3 delta=p-nodeLights[b].xyz;
    if(dot(delta,delta)>=nodeLights[b].w*nodeLights[b].w)return false;
    if(nodeLights[b+1].w< -1.0)return true;
    float axial=dot(delta,nodeLights[b+1].xyz);
    if(axial<=0.0)return false;
    if(nodeAreaEmitter(index))return true;
    vec3 radial=delta-nodeLights[b+1].xyz*axial;
    float extent=nodeLights[b+5].y+axial*nodeLights[b+4].w+0.0001;
    return dot(radial,radial)<=extent*extent;
}

vec3 nodeDirectGeo(int index,vec3 p,vec3 normal,vec3 geometryNormal) {
    if(nodeVolumeProbeActive())return vec3(0.0);
    if(!nodeLightCanReach(index,p))return vec3(0.0);
    float normalLength=dot(normal,normal),geoLength=dot(geometryNormal,geometryNormal);
    if(!(normalLength>1e-12) || !(geoLength>1e-12)
            || isinf(normalLength) || isinf(geoLength))return vec3(0.0);
    vec3 n=normal*inversesqrt(normalLength),receiver=p+geometryNormal*inversesqrt(geoLength)*0.015;
    // A sphere enclosing the emitter also bounds every receiver cosine.
    if(dot(nodeLights[index*12].xyz-p,n)+nodeLights[index*12+5].y< -0.0001)return vec3(0.0);
    int samples=nodeEmitterSamples(index);
    NodeEmitterDomain domain=nodeEmitterDomain(index,p);
    if(domain.ratio<=0.0)return vec3(0.0);
    vec3 centerDelta=p-nodeLights[index*12].xyz;
    float centerDistanceSquared=dot(centerDelta,centerDelta);
    float attenuation=nodeRangeAttenuation(index,centerDistanceSquared);
    bool prepared=false,worldClear=false;
    int tiles=0,worldTile=-1,modelMask=255;
    ivec2 entities=ivec2(0);
#ifndef NODE_LIGHT_NO_ENTITY_SHADOWS
    entities.y=nodeEntityRange.x;
#endif
    vec3 sum=vec3(0.0);
    for(int s=0;s<samples;s++) {
        vec3 source;float weight;nodeEmitterSample(index,p,s,samples,domain,source,weight);
        if(weight<=0.0)continue;
        vec3 delta=source-p;
        float cosine=max(dot(n,delta)*inversesqrt(max(dot(delta,delta),1e-12)),0.0);
        if(cosine<=0.0)continue;
        vec3 light=nodeSampleRadiancePrepared(index,p,source,centerDistanceSquared,attenuation)*(weight*cosine);
        if(max(light.r,max(light.g,light.b))>0.000001) {
            // Prepare only for the first contributing sample. The biased receiver
            // and enclosing emitter sphere cover every subsequent shadow ray.
            if(!prepared && nodeLights[index*12+5].x>=0.5) {
                tiles=nodeWorldTileLimit();
                if(samples>1) {
                    float radius=nodeLights[index*12+5].y;
                    vec3 center=nodeLights[index*12].xyz;
                    worldClear=nodeVolumeWorldClear(center,receiver,receiver,radius,tiles,worldTile);
                    modelMask=nodeVolumeModelMask(center,receiver,receiver,radius);
                    entities=nodeVolumeEntityRange(center,receiver,receiver,radius);
                }
                prepared=true;
            }
            sum+=light*nodeSampleTransmission(index,receiver,source,entities,tiles,worldClear,worldTile,modelMask);
        }
    }
    return sum/float(samples);
}
vec3 nodeDirect(int index,vec3 p,vec3 normal) {return nodeDirectGeo(index,p,normal,normal);}

// Clip a finite ray interval to the forward spotlight cone. Integrating the
// overlap of each marching cell avoids turning entire cells on/off at its edge.
bool nodeVolumeCone(vec3 origin,vec3 ray,vec3 axis,float outer,inout float start,inout float finish) {
    if(outer<=0.0)return true;
    float axial=dot(origin,axis),slope=dot(ray,axis);
    if(abs(slope)<1e-7) {if(axial<=0.0)return false;}
    else if(slope>0.0)start=max(start,-axial/slope);
    else finish=min(finish,-axial/slope);
    if(finish<=start)return false;
    float cosineSquared=outer*outer;
    float a=slope*slope-cosineSquared;
    float b=2.0*(axial*slope-cosineSquared*dot(origin,ray));
    float c=axial*axial-cosineSquared*dot(origin,origin);
    if(abs(a)<1e-7) {
        if(abs(b)<1e-7)return c>=0.0;
        if(b>0.0)start=max(start,-c/b);else finish=min(finish,-c/b);
    } else {
        float discriminant=b*b-4.0*a*c;
        if(discriminant<=0.0)return a>0.0;
        float root=sqrt(discriminant);
        // Stable quadratic roots also retain narrow cones near a distant eye.
        float q=-0.5*(b+(b>=0.0?root:-root));
        float r0=q/a,r1=abs(q)>1e-20?c/q:-b/(2.0*a);
        float low=min(r0,r1),high=max(r0,r1);
        if(a<0.0) {start=max(start,low);finish=min(finish,high);}
        else if(start<low)finish=min(finish,low);
        else start=max(start,high);
    }
    return finish>start;
}

// NODE_LIGHT_FROXELS_INCLUDE

vec3 nodeVolumeSegment(int index,vec3 endpoint,float nearDistance) {
    return nodeFroxelSegment(index,endpoint,nearDistance,length(endpoint-nodeEyeOffset));
}
vec3 nodeVolumeTransmitted(int index,vec3 endpoint) {return nodeFroxelSample(index,endpoint);}
vec3 nodeVolume(int index,vec3 endpoint) {return nodeFroxelSample(index,endpoint);}

vec3 nodeVolumeTransmittedResult(vec3 endpoint) {
    if(nodeLightCount<=0)return vec3(0.0);
    vec3 sum=vec3(0.0);
    for(int i=0;i<min(nodeLightCount,8);i++)sum+=nodeFroxelSample(i,endpoint);
    return sum;
}

vec3 nodeVolumeBetween(vec3 front,vec3 back) {
    if(nodeLightCount<=0)return vec3(0.0);
    vec3 sum=vec3(0.0);
    for(int i=0;i<min(nodeLightCount,8);i++)sum+=nodeFroxelBetween(i,front,back);
    return sum;
}

vec3 nodeVolumeBetweenResult(vec3 front,vec3 back,vec2 uv) {
    return nodeVolumeBetween(front,back);
}

vec3 nodeReconstruct(sampler2D atlas,vec4 rect,vec2 uv,float depth,float depthLimit) {
    depth=min(depth,depthLimit);
    vec2 size=vec2(textureSize(atlas,0)),pixel=uv*rect.xy*size-0.5;
    vec2 base=floor(pixel),fraction=fract(pixel);vec3 sum=vec3(0);float weights=0.0;
    for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
        vec2 offset=vec2(x,y),p=clamp(base+offset,vec2(0),rect.xy*size-1.0);
        vec4 value=texelFetch(atlas,ivec2(p+rect.zw*size),0);
        float weight=(x==0?1.0-fraction.x:fraction.x)*(y==0?1.0-fraction.y:fraction.y);
        weight*=exp(-abs(min(value.a,depthLimit)-depth)/max(0.05,depth*0.002));
        sum+=value.rgb*weight;weights+=weight;
    }
    return weights>0.00001?sum/weights:vec3(-1.0);
}
vec3 nodeReconstruct(sampler2D atlas,vec4 rect,vec2 uv,float depth) {
    return nodeReconstruct(atlas,rect,uv,depth,1e30);
}
vec3 nodeVolumeReconstruct(int index,vec3 p,vec2 uv) {
    return nodeFroxelSample(index,p);
}
vec3 nodeSurfacePassGeo(vec3 p,vec3 normal,vec3 geo,vec2 uv,bool prepass) {
    if(nodeLightCount<=0 || (!prepass && nodeVolumeCostSkip(128)))return vec3(0.0);
    vec3 sum=vec3(0);
    int first=prepass?nodeLightIndex:0;
    int end=prepass?first+1:min(nodeLightCount,8);
    for(int i=first;i<end;i++) {
        bool reconstruct=!prepass && nodeLightResolved!=0 && nodeLights[i*12+5].w<1.0;
        if(reconstruct)nodeVolumeProbeRecord(2);
        vec3 value=reconstruct?nodeReconstruct(nodeLightSurfaceAtlas,nodeSurfaceRects[i],uv,length(p)):vec3(-1);
        if(value.x>=0.0)sum+=value;
        else {
            if(reconstruct)nodeVolumeProbeRecord(4);
            if(!reconstruct || !nodeVolumeCostSkip(256))sum+=nodeDirectGeo(i,p,normal,geo);
        }
    }
    return sum;
}
vec3 nodeSurfaceGeo(vec3 p,vec3 normal,vec3 geo,vec2 uv) {return nodeSurfacePassGeo(p,normal,geo,uv,false);}
vec3 nodeSurface(vec3 p,vec3 normal,vec2 uv) {return nodeSurfaceGeo(p,normal,normal,uv);}
vec3 nodeVolumeResult(vec3 p,vec2 uv) {
    if(nodeLightCount<=0 || nodeVolumeCostSkip(32))return vec3(0.0);
    vec3 sum=vec3(0);
    for(int i=0;i<min(nodeLightCount,8);i++) {
        if(nodeLights[i*12+6].x<0.5 || nodeLights[i*12+6].y<=0.0 || nodeLights[i*12+6].z<=0.0)continue;
        sum+=nodeFroxelSample(i,p);
    }
    return sum;
}

// Intersect the view ray with the emitting disk, bounded by the scene depth.
// This is independent of shadow enablement and is never evaluated in prepasses.
vec3 nodeSourceInterval(vec3 endpoint,bool sky,float nearDistance,bool cameraTransmission) {
    if(nodeLightCount<=0 || nodeVolumeProbeActive() || nodeVolumeCostSkip(64))return vec3(0.0);
    vec3 offset=endpoint-nodeEyeOffset;
    float distanceToScene=length(offset);
    if(distanceToScene<0.000001)return vec3(0);
    vec3 ray=offset/distanceToScene;
    float nearest=sky?1e20:distanceToScene;
    vec3 result=vec3(0);
    for(int i=0;i<min(nodeLightCount,8);i++) {
        int b=i*12;
        float radius=nodeLights[b+5].y;
        if(nodeLights[b+9].z<0.5 || radius<=0.0)continue;
        vec3 normal=nodeLights[b+1].xyz;
        float cosine=dot(normal,-ray),outer=nodeLights[b+1].w,inner=nodeLights[b+3].w;
        if(outer< -1.0) {
            vec3 toSource=nodeLights[b].xyz-nodeEyeOffset;
            float along=dot(toSource,ray);
            vec3 closest=toSource-ray*along;
            float square=radius*radius-dot(closest,closest);
            if(square<0.0)continue;
            float root=sqrt(square),t=along-root;
            if(t<=0.0)t=along+root;
            if(t<=0.0 || t>=nearest)continue;
            result=t>nearDistance?nodeLights[b+2].rgb*nodeLights[b+2].w/max(3.14159265*radius*radius,1e-6):vec3(0);
            nearest=t;
            continue;
        }
        if(cosine<=outer)continue;
        float t=dot(nodeLights[b].xyz-nodeEyeOffset,normal)/(-cosine);
        if(t<=0.0 || t>=nearest)continue;
        vec3 delta=nodeEyeOffset+ray*t-nodeLights[b].xyz;
        bool area=nodeAreaEmitter(i);
        if(area) {
            if(abs(dot(delta,nodeLights[b+3].xyz))>nodeLights[b+7].z
                    || abs(dot(delta,nodeLights[b+4].xyz))>nodeLights[b+10].w)continue;
        } else if(dot(delta,delta)>radius*radius)continue;
        float edge=inner>outer+0.00001?smoothstep(outer,inner,cosine):1.0;
        float surfaceArea=area?4.0*nodeLights[b+7].z*nodeLights[b+10].w:3.14159265*radius*radius;
        result=t>nearDistance?nodeLights[b+2].rgb*(nodeLights[b+2].w*edge)/max(surfaceArea,1e-6):vec3(0.0);
        nearest=t;
    }
    if(cameraTransmission && any(greaterThan(result,vec3(0.0))))
        result*=exp(-nodeCameraOpticalDepth(nodeEyeOffset,nodeEyeOffset+ray*nearest));
    return result;
}

vec3 nodeSourceSegment(vec3 endpoint,bool sky,float nearDistance) {return nodeSourceInterval(endpoint,sky,nearDistance,false);}
vec3 nodeSourceTransmitted(vec3 endpoint,bool sky) {return nodeSourceInterval(endpoint,sky,0.0,true);}
vec3 nodeSourceResult(vec3 endpoint,bool sky) {return nodeSourceSegment(endpoint,sky,0.0);}
