// Each light owns a tiled, view-aligned volume. Texels store local or cumulative
// (scattered radiance, scalar fog transmittance); scan passes use the same layout.
uniform ivec4 nodeFroxelRects[8];
uniform ivec2 nodeFroxelShape[8];
uniform vec4 nodeFroxelDepths[8];
// Grid endpoints, geometric coverage numerator and finite-disk cone offset.
uniform vec4 nodeFroxelConstants[8];
uniform vec4 nodeFroxelViews[8];
uniform vec3 nodeFroxelRays[4];
uniform mat4 nodeFroxelViewProjection;
uniform int nodeFroxelReady;

float nodeFroxelBoundary(int index,float slice) {
    vec4 depth=nodeFroxelDepths[index];
    float fraction=clamp(slice/float(max(nodeFroxelShape[index].x,1)),0.0,1.0);
    return max(0.0,depth.x+depth.y*sinh(mix(depth.z,depth.w,fraction)));
}

ivec2 nodeFroxelAddress(int index,ivec2 xy,int slice) {
    ivec4 rect=nodeFroxelRects[index];
    int columns=max(nodeFroxelShape[index].y,1);
    return rect.xy+ivec2(slice%columns,slice/columns)*rect.zw+xy;
}

vec4 nodeFroxelFetch(int index,ivec2 xy,int slice) {
    if(slice<0)return vec4(0.0,0.0,0.0,1.0);
    return texelFetch(nodeLightVolumeAtlas,nodeFroxelAddress(index,xy,slice),0);
}

// Inclusive parallel prefix of the associative front-to-back transport operator.
vec4 nodeFroxelScan(ivec2 pixel,int index,int offset) {
    ivec4 rect=nodeFroxelRects[index];
    ivec2 local=pixel-rect.xy;
    ivec2 tile=local/rect.zw;
    int slice=tile.y*nodeFroxelShape[index].y+tile.x;
    if(slice>=nodeFroxelShape[index].x)return vec4(0.0,0.0,0.0,1.0);
    ivec2 xy=local-tile*rect.zw;
    // Tiny grids spend more on pass setup than on a short raw-cell prefix.
    // The caller selects this only for <=4096 cells and <=32 depth slices.
    if(offset==0) {
        vec4 sum=vec4(0.0,0.0,0.0,1.0);
        for(int z=0;z<=slice;z++) {
            vec4 cell=nodeFroxelFetch(index,xy,z);
            sum=vec4(sum.rgb+sum.a*cell.rgb,sum.a*cell.a);
        }
        return sum;
    }
    vec4 value=nodeFroxelFetch(index,xy,slice);
    if(slice<offset)return value;
    vec4 previous=nodeFroxelFetch(index,xy,slice-offset);
    return vec4(previous.rgb+previous.a*value.rgb,previous.a*value.a);
}

vec3 nodeFroxelColumn(int index,ivec2 xy,int slice,float fraction,float litFraction) {
    vec4 first=nodeFroxelFetch(index,xy,slice-1);
    vec4 last=nodeFroxelFetch(index,xy,slice);
    // Recover the partial-cell Beer integral from two prefixes. Very opaque
    // prefixes already have negligible remaining energy and need no division.
    float transmission=first.a>0.000001?clamp(last.a/first.a,0.0,1.0):1.0;
    // Alpha covers the fog-sphere interval, but scattering may occupy only
    // its cone-clipped subset. Use that subset's optical depth for the ratio.
    if(litFraction<0.99999)transmission=pow(max(transmission,1e-20),litFraction);
    float partial=pow(max(transmission,1e-20),fraction);
    float weight=transmission<0.9999?(1.0-partial)/(1.0-transmission):fraction;
    return first.rgb+max(last.rgb-first.rgb,vec3(0.0))*weight;
}

struct NodeFroxelQuery {
    vec2 pixel;
    float start;
    float finish;
    vec2 medium;
    float coverage;
};

// Front/back prefixes use the same projected ray, cone support and edge coverage.
bool nodeFroxelPrepare(int index,vec3 endpoint,float distanceToEye,out NodeFroxelQuery query) {
    if(nodeFroxelReady==0 || nodeFroxelShape[index].x<=0 || nodeFroxelRects[index].z<=0
            || nodeFroxelRects[index].w<=0 || nodeVolumeCostSkip(32))return false;
    nodeVolumeProbeRecord(1);
    if(nodeVolumeProbeActive())return false;
    vec3 direction=endpoint-nodeEyeOffset;
    float rayLength=length(direction);
    if(rayLength<=0.000001)return false;
    vec3 ray=direction/rayLength,source=nodeLights[index*12].xyz-nodeEyeOffset;
    float center=dot(source,ray);
    vec3 closest=ray*center-source;
    float sphere=nodeLights[index*12].w*nodeLights[index*12].w-dot(closest,closest);
    if(sphere<=0.0)return false;
    float root=sqrt(sphere);
    float sphereNear=max(0.0,center-root);
    if(distanceToEye<=sphereNear)return false;
    int b=index*12;
    float coneNear=sphereNear-center;
    float coneFar=root;
    float radius=nodeLights[b+5].y;
    vec3 coneOrigin=closest;
    if(!nodeAreaEmitter(index) && nodeLights[b+1].w>=-1.0)
        coneOrigin+=nodeLights[b+1].xyz*nodeFroxelConstants[index].w;
    if(!nodeVolumeCone(coneOrigin,ray,nodeLights[b+1].xyz,nodeLights[b+1].w,coneNear,coneFar))return false;
    if(radius>0.0 && nodeLights[b+1].w>=-1.0) {
        float axial=dot(closest,nodeLights[b+1].xyz),slope=dot(ray,nodeLights[b+1].xyz);
        if(abs(slope)<1e-7) {if(axial<=0.0)return false;}
        else if(slope>0.0)coneNear=max(coneNear,-axial/slope);
        else coneFar=min(coneFar,-axial/slope);
        if(coneFar<=coneNear)return false;
    }
    query.start=max(sphereNear,max(coneNear+center,nodeFroxelConstants[index].x));
    query.finish=min(center+root,coneFar+center);
    query.medium=vec2(sphereNear,center+root);
    if(distanceToEye<=query.start)return false;
    vec4 clip=nodeFroxelViewProjection*vec4(endpoint,1.0);
    if(clip.w<=0.000001)return false;
    vec2 uv=clip.xy/clip.w*0.5+0.5;
    uv=(uv-nodeFroxelViews[index].xy)/nodeFroxelViews[index].zw;
    if(any(lessThan(uv,vec2(0.0))) || any(greaterThan(uv,vec2(1.0))))return false;
    query.pixel=uv*vec2(nodeFroxelRects[index].zw)-0.5;
    // A coarse texel may remain bright up to the exact cone boundary. Fade only
    // within a quarter-froxel geometric footprint, using the complete ray support
    // so front/back prefixes receive the same factor and remain monotone.
    vec2 screenUv=nodeFroxelViews[index].xy+uv*nodeFroxelViews[index].zw;
    vec3 viewRay=mix(mix(nodeFroxelRays[0],nodeFroxelRays[1],screenUv.x),
            mix(nodeFroxelRays[2],nodeFroxelRays[3],screenUv.x),screenUv.y);
    float footprint=max(0.00001,nodeFroxelConstants[index].z/max(length(viewRay),0.000001));
    query.coverage=smoothstep(0.0,footprint,coneFar-coneNear);
    return true;
}

vec3 nodeFroxelSamplePrepared(int index,NodeFroxelQuery query,float distanceToEye) {
    if(distanceToEye<=query.start)return vec3(0.0);
    vec4 depths=nodeFroxelDepths[index];
    int slices=nodeFroxelShape[index].x;
    ivec2 base=ivec2(floor(query.pixel)),maximum=nodeFroxelRects[index].zw-ivec2(1);
    vec2 blend=fract(query.pixel);
    // Directional emitters benefit from a completed-prefix lookup. Keep the broad
    // point-light path straight: divergent early returns can outweigh saved fetches.
    if(nodeLights[index*12+1].w>=-1.0 && distanceToEye>=nodeFroxelConstants[index].y) {
        vec3 lowerLeft=nodeFroxelFetch(index,clamp(base,ivec2(0),maximum),slices-1).rgb;
        vec3 lowerRight=nodeFroxelFetch(index,clamp(base+ivec2(1,0),ivec2(0),maximum),slices-1).rgb;
        vec3 upperLeft=nodeFroxelFetch(index,clamp(base+ivec2(0,1),ivec2(0),maximum),slices-1).rgb;
        vec3 upperRight=nodeFroxelFetch(index,clamp(base+ivec2(1),ivec2(0),maximum),slices-1).rgb;
        return mix(mix(lowerLeft,lowerRight,blend.x),mix(upperLeft,upperRight,blend.x),blend.y)*query.coverage;
    }
    float coordinate=(asinh((distanceToEye-depths.x)/max(depths.y,0.0001))-depths.z)
            /max(depths.w-depths.z,0.000001)*float(slices);
    int slice=clamp(int(floor(coordinate)),0,slices-1);
    float low=nodeFroxelBoundary(index,float(slice)),high=nodeFroxelBoundary(index,float(slice+1));
    // Injection integrates only the sphere/cone overlap of this cell. Spreading
    // that energy over the full depth slice produces a jump at the entry point.
    float litLow=max(low,query.start),litHigh=min(high,query.finish);
    float litLength=max(0.0,litHigh-litLow);
    float mediumLength=max(0.000001,min(high,query.medium.y)-max(low,query.medium.x));
    float fraction=litLength>0.000001?clamp((distanceToEye-litLow)/litLength,0.0,1.0):1.0;
    float litFraction=litLength>0.000001?clamp(litLength/mediumLength,0.0,1.0):1.0;
    vec3 lowerLeft=nodeFroxelColumn(index,clamp(base,ivec2(0),maximum),slice,fraction,litFraction);
    vec3 lowerRight=nodeFroxelColumn(index,clamp(base+ivec2(1,0),ivec2(0),maximum),slice,fraction,litFraction);
    vec3 upperLeft=nodeFroxelColumn(index,clamp(base+ivec2(0,1),ivec2(0),maximum),slice,fraction,litFraction);
    vec3 upperRight=nodeFroxelColumn(index,clamp(base+ivec2(1),ivec2(0),maximum),slice,fraction,litFraction);
    return mix(mix(lowerLeft,lowerRight,blend.x),mix(upperLeft,upperRight,blend.x),blend.y)*query.coverage;
}

vec3 nodeFroxelSampleDepth(int index,vec3 endpoint,float distanceToEye) {
    NodeFroxelQuery query;
    if(!nodeFroxelPrepare(index,endpoint,distanceToEye,query))return vec3(0.0);
    return nodeFroxelSamplePrepared(index,query,distanceToEye);
}

vec3 nodeFroxelSample(int index,vec3 endpoint) {
    return nodeFroxelSampleDepth(index,endpoint,length(endpoint-nodeEyeOffset));
}

vec3 nodeFroxelSegment(int index,vec3 back,float nearDistance,float farDistance) {
    if(farDistance<=nearDistance)return vec3(0.0);
    NodeFroxelQuery query;
    if(!nodeFroxelPrepare(index,back,farDistance,query))return vec3(0.0);
    return max(nodeFroxelSamplePrepared(index,query,farDistance)
            -nodeFroxelSamplePrepared(index,query,nearDistance),vec3(0.0));
}

vec3 nodeFroxelBetween(int index,vec3 front,vec3 back) {
    return nodeFroxelSegment(index,back,length(front-nodeEyeOffset),length(back-nodeEyeOffset));
}

#ifdef NODE_LIGHT_FROXEL_INJECT
uniform int nodeFroxelCameraTransmission;

vec4 nodeFroxelInject(int index,vec2 uv,int slice) {
    uv=nodeFroxelViews[index].xy+uv*nodeFroxelViews[index].zw;
    int b=index*12;
    if(slice>=nodeFroxelShape[index].x || nodeLights[b+6].x<0.5
            || nodeLights[b+6].y<=0.0 || nodeLights[b+6].z<=0.0)
        return vec4(0.0,0.0,0.0,1.0);
    vec3 ray=normalize(mix(mix(nodeFroxelRays[0],nodeFroxelRays[1],uv.x),
            mix(nodeFroxelRays[2],nodeFroxelRays[3],uv.x),uv.y));
    vec3 source=nodeLights[b].xyz-nodeEyeOffset;
    float center=dot(source,ray);
    vec3 closest=ray*center-source;
    float discriminant=nodeLights[b].w*nodeLights[b].w-dot(closest,closest);
    if(discriminant<=0.0)return vec4(0.0,0.0,0.0,1.0);
    float root=sqrt(discriminant);
    float first=max(nodeFroxelBoundary(index,float(slice)),max(0.0,center-root));
    float last=min(nodeFroxelBoundary(index,float(slice+1)),center+root);
    if(last<=first)return vec4(0.0,0.0,0.0,1.0);
    float density=nodeLights[b+6].z;
    if(nodeLights[b+6].x>1.5) {
        vec3 midpoint=nodeEyeOffset+ray*((first+last)*0.5);
        vec3 noisePosition=midpoint/nodeLights[b+7].y+nodeLights[b+11].xyz+vec3(nodeLights[b+7].w);
        noisePosition+=nodeLightTime*nodeLights[b+10].xyz/nodeLights[b+7].y;
        density*=mix(1.0,2.0*nodeNoise(noisePosition),nodeLights[b+7].x);
    }
    float transmission=exp(-density*(last-first));
    // Extinction exists throughout the fog sphere, including its unlit cone
    // region. Only scattering gets cone-clipped or multiplied by source onset.
    float litFirst=first-center,litLast=last-center;
    float radius=nodeLights[b+5].y;
    bool area=nodeAreaEmitter(index),disk=!area && radius>0.0 && nodeLights[b+1].w>=-1.0;
    vec3 coneOrigin=closest;
    if(!area && nodeLights[b+1].w>=-1.0)
        coneOrigin+=nodeLights[b+1].xyz*(radius/max(nodeLights[b+4].w,0.0001));
    if(!nodeVolumeCone(coneOrigin,ray,nodeLights[b+1].xyz,nodeLights[b+1].w,litFirst,litLast))
        return vec4(0.0,0.0,0.0,transmission);
    if(radius>0.0 && nodeLights[b+1].w>=-1.0) {
        float axial=dot(closest,nodeLights[b+1].xyz),slope=dot(ray,nodeLights[b+1].xyz);
        if(abs(slope)<1e-7) {
            if(axial<=0.0)return vec4(0.0,0.0,0.0,transmission);
        } else if(slope>0.0)litFirst=max(litFirst,-axial/slope);
        else litLast=min(litLast,-axial/slope);
    }
    litFirst+=center;litLast+=center;
    if(litLast<=litFirst)return vec4(0.0,0.0,0.0,transmission);
    vec3 p=nodeEyeOffset+ray*((litFirst+litLast)*0.5);
    float onset=1.0;
    if(disk) {
        vec3 local=p-nodeLights[b].xyz;
        float axial=dot(local,nodeLights[b+1].xyz);
        float cap=1.0-length(local-nodeLights[b+1].xyz*radius)/radius;
        onset=smoothstep(0.0,0.25,axial/radius)
                *mix(smoothstep(-0.2,0.2,cap),1.0,smoothstep(0.5,1.0,axial/radius));
    }
    if(onset<=0.0)return vec4(0.0,0.0,0.0,transmission);
    NodeEmitterDomain domain=nodeEmitterDomain(index,p);
    if(domain.ratio<=0.0)return vec4(0.0,0.0,0.0,transmission);
    bool shadows=nodeLights[b+5].x>=0.5;
    int tiles=shadows || nodeFroxelCameraTransmission!=0?nodeWorldTileLimit():0;
    int worldTile=-1;
    bool worldClear=shadows && nodeVolumeWorldClear(nodeLights[b].xyz,p,p,radius,tiles,worldTile);
    int modelMask=shadows?nodeVolumeModelMask(nodeLights[b].xyz,p,p,radius):0;
    ivec2 entities=shadows?nodeVolumeEntityRange(nodeLights[b].xyz,p,p,radius):ivec2(0);
    int samples=radius<=0.0?1:clamp(int(nodeLights[b+9].w),4,16);
    float g=nodeLights[b+6].w,numerator=(1.0-g*g)/12.5663706,phaseBase=1.0+g*g;
    vec3 delta=p-nodeLights[b].xyz;
    float distanceSquared=dot(delta,delta),attenuation=nodeRangeAttenuation(index,distanceSquared);
    vec3 sum=vec3(0.0);
    for(int s=0;s<samples;s++) {
        vec3 emitter;float weight;
        if(area)nodeAreaEmitterSample(index,p,s,samples,false,emitter,weight);
        else nodeEmitterSample(index,p,s,samples,domain,emitter,weight);
        if(weight<=0.0)continue;
        vec3 radiance=nodeSampleRadiancePrepared(index,p,emitter,distanceSquared,attenuation)*weight;
        if(max(radiance.r,max(radiance.g,radiance.b))<=0.000001)continue;
        vec3 fromLight=p-emitter;
        float cosine=clamp(dot(fromLight*inversesqrt(max(dot(fromLight,fromLight),1e-12)),-ray),-1.0,1.0);
        float denominator=max(phaseBase-2.0*g*cosine,0.001);
        float phase=numerator*inversesqrt(denominator)/denominator;
        sum+=phase*radiance*nodeSampleTransmission(index,p,emitter,entities,tiles,worldClear,worldTile,modelMask);
    }
    float contribution=exp(-density*(litFirst-first))*(1.0-exp(-density*(litLast-litFirst)));
    sum*=onset*contribution*nodeLights[b+6].y/float(samples);
    if(nodeFroxelCameraTransmission!=0)sum*=exp(-nodeCameraOpticalDepth(nodeEyeOffset,p,tiles));
    return vec4(sum,transmission);
}
#endif
