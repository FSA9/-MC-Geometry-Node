import os
import cv2
import json
import numpy as np
from pathlib import Path
from tqdm import tqdm
from ultralytics import YOLO


# ==========================================
# 算法核心模块：基础几何计算与清洗
# ==========================================
def merge_abnormal_cells(boxes, img_w, img_h, p=0.01):
    if not boxes: return []
    th_w = img_w * p
    th_h = img_h * p
    active_boxes = [list(b) for b in boxes]

    while True:
        merged = False
        for i in range(len(active_boxes)):
            box = active_boxes[i]
            w = box[2] - box[0]
            h = box[3] - box[1]

            is_short = h < th_h
            is_narrow = w < th_w

            if not is_short and not is_narrow:
                continue

            best_target_idx = -1
            min_dist = float('inf')

            if is_short:
                for j, t_box in enumerate(active_boxes):
                    if i == j: continue
                    x_overlap = max(0, min(box[2], t_box[2]) - max(box[0], t_box[0]))
                    if x_overlap > 0:
                        cy_t = (t_box[1] + t_box[3]) / 2.0
                        cy_b = (box[1] + box[3]) / 2.0
                        if cy_t < cy_b:
                            dist = abs(cy_b - cy_t)
                            if dist < min_dist:
                                min_dist = dist
                                best_target_idx = j

            if best_target_idx == -1 and is_narrow:
                for j, t_box in enumerate(active_boxes):
                    if i == j: continue
                    y_overlap = max(0, min(box[3], t_box[3]) - max(box[1], t_box[1]))
                    if y_overlap > 0:
                        cx_t = (t_box[0] + t_box[2]) / 2.0
                        cx_b = (box[0] + box[2]) / 2.0
                        if cx_t < cx_b:
                            dist = abs(cx_b - cx_t)
                            if dist < min_dist:
                                min_dist = dist
                                best_target_idx = j

            if best_target_idx != -1:
                target = active_boxes[best_target_idx]
                target[0] = min(target[0], box[0])
                target[1] = min(target[1], box[1])
                target[2] = max(target[2], box[2])
                target[3] = max(target[3], box[3])

                active_boxes.pop(i)
                merged = True
                break
            else:
                active_boxes.pop(i)
                merged = True
                break

        if not merged:
            break

    return active_boxes


def box_ioa(box_small, box_large):
    x_left = max(box_small[0], box_large[0])
    y_top = max(box_small[1], box_large[1])
    x_right = min(box_small[2], box_large[2])
    y_bottom = min(box_small[3], box_large[3])
    if x_right < x_left or y_bottom < y_top:
        return 0.0
    intersection_area = (x_right - x_left) * (y_bottom - y_top)
    small_area = (box_small[2] - box_small[0]) * (box_small[3] - box_small[1])
    return intersection_area / float(small_area)


def clean_boxes(boxes, ioa_thresh=0.85):
    if not boxes: return []
    keep = []
    boxes = sorted(boxes, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
    for i, box in enumerate(boxes):
        is_contained = False
        for j, other_box in enumerate(boxes):
            if i == j: continue
            if box_ioa(box, other_box) > ioa_thresh:
                is_contained = True
                break
        if not is_contained:
            keep.append(box)
    return keep


# ==========================================
# 核心升级：动态防御型聚类
# ==========================================
def get_dynamic_grid_lines(coords_with_size, dynamic_ratio=0.1, min_pixel_tol=3):
    if not coords_with_size: return []
    coords_with_size = sorted(coords_with_size, key=lambda x: x[0])

    lines = []
    current_cluster = [coords_with_size[0]]

    for i in range(1, len(coords_with_size)):
        curr_val, curr_size = coords_with_size[i]
        cluster_val = np.mean([item[0] for item in current_cluster])
        cluster_size = np.mean([item[1] for item in current_cluster])

        dynamic_thresh = max(min_pixel_tol, dynamic_ratio * min(curr_size, cluster_size))

        if curr_val - cluster_val < dynamic_thresh:
            current_cluster.append(coords_with_size[i])
        else:
            lines.append(int(np.mean([item[0] for item in current_cluster])))
            current_cluster = [coords_with_size[i]]

    lines.append(int(np.mean([item[0] for item in current_cluster])))
    return sorted(lines)


# ==========================================
# 算法核心模块：网格量化与结构修复
# ==========================================
def preprocess_boxes_alignment(
        boxes,
        dynamic_thresh_ratio=0.1,
        wide_cell_ratio=1.2,
        edge_tol_ratio=0.8
):
    if not boxes: return []

    widths = [b[2] - b[0] for b in boxes]
    median_w = np.median(widths)

    x_coords_with_widths = [(b[0], b[2] - b[0]) for b in boxes] + [(b[2], b[2] - b[0]) for b in boxes]
    y_coords_with_heights = [(b[1], b[3] - b[1]) for b in boxes] + [(b[3], b[3] - b[1]) for b in boxes]

    x_lines = get_dynamic_grid_lines(x_coords_with_widths, dynamic_ratio=dynamic_thresh_ratio)
    y_lines = get_dynamic_grid_lines(y_coords_with_heights, dynamic_ratio=dynamic_thresh_ratio)

    if len(x_lines) < 2 or len(y_lines) < 2:
        return boxes

    MAX_COL = len(x_lines) - 1
    MAX_ROW = len(y_lines) - 1

    row_boxes = [[] for _ in range(MAX_ROW)]
    for b in boxes:
        cy = (b[1] + b[3]) / 2.0
        r_idx = 0
        for r in range(MAX_ROW):
            if y_lines[r] <= cy <= y_lines[r + 1]:
                r_idx = r
                break
            elif cy < y_lines[0]:
                r_idx = 0
            elif cy > y_lines[-1]:
                r_idx = MAX_ROW - 1
        row_boxes[r_idx].append(b)

    final_aligned_boxes = []

    for r in range(MAX_ROW):
        y1_aligned = y_lines[r]
        y2_aligned = y_lines[r + 1]

        if not row_boxes[r]:
            for c in range(MAX_COL):
                final_aligned_boxes.append([x_lines[c], y1_aligned, x_lines[c + 1], y2_aligned])
            continue

        current_row = sorted(row_boxes[r], key=lambda b: b[0])
        spans = []
        for b in current_row:
            c_start = min(range(len(x_lines)), key=lambda i: abs(x_lines[i] - b[0]))
            c_end = min(range(len(x_lines)), key=lambda i: abs(x_lines[i] - b[2]))
            if c_start == c_end:
                c_end = min(c_start + 1, MAX_COL)
                if c_start == c_end: c_start = max(0, c_end - 1)
            spans.append([c_start, c_end, b])

        if spans:
            first_span = spans[0]
            b_first = first_span[2]
            if first_span[0] > 0:
                is_wide = (b_first[2] - b_first[0] > wide_cell_ratio * median_w)
                is_close = (b_first[0] - x_lines[0] < edge_tol_ratio * median_w)
                if is_wide or is_close:
                    first_span[0] = 0

            last_span = spans[-1]
            b_last = last_span[2]
            if last_span[1] < MAX_COL:
                is_wide = (b_last[2] - b_last[0] > wide_cell_ratio * median_w)
                is_close = (x_lines[-1] - b_last[2] < edge_tol_ratio * median_w)
                if is_wide or is_close:
                    last_span[1] = MAX_COL

        current_col = 0
        for span in spans:
            c_start, c_end, b = span
            if c_start > current_col:
                final_aligned_boxes.append([x_lines[current_col], y1_aligned, x_lines[c_start], y2_aligned])

            actual_start = max(current_col, c_start)
            if actual_start < c_end:
                final_aligned_boxes.append([x_lines[actual_start], y1_aligned, x_lines[c_end], y2_aligned])
            current_col = max(current_col, c_end)

        if current_col < MAX_COL:
            final_aligned_boxes.append([x_lines[current_col], y1_aligned, x_lines[-1], y2_aligned])

    return final_aligned_boxes

def generate_html_from_aligned_boxes(aligned_boxes, img_w, img_h):
    if not aligned_boxes: return ""

    x_lines = sorted(list(set(int(b[0]) for b in aligned_boxes) | set(int(b[2]) for b in aligned_boxes)))
    y_lines = sorted(list(set(int(b[1]) for b in aligned_boxes) | set(int(b[3]) for b in aligned_boxes)))
    MAX_ROW = len(y_lines) - 1

    html_content = [
        # "<!DOCTYPE html>",
        # "<html>", "<head>", "<meta charset='utf-8'>", "<style>",
        # "  table { border-collapse: collapse; width: 100%; font-family: sans-serif; }",
        # "  td { border: 1px solid black; padding: 10px; text-align: center; min-height: 30px; }",
        # "</style>", "</head>", "<body>", "<table>"
        "<table>"
    ]

    cells_info = []
    for b in aligned_boxes:
        c_start, c_end = x_lines.index(int(b[0])), x_lines.index(int(b[2]))
        r_start, r_end = y_lines.index(int(b[1])), y_lines.index(int(b[3]))
        cells_info.append({
            'r_start': r_start, 'c_start': c_start,
            'rowspan': r_end - r_start, 'colspan': c_end - c_start,
            'box': b
        })

    for r in range(MAX_ROW):
        html_content.append("  <tr>")
        row_cells = sorted([c for c in cells_info if c['r_start'] == r], key=lambda x: x['c_start'])
        for cell in row_cells:
            # 提取原图对齐后的坐标
            b = cell['box']
            # 坐标归一化 0-1000
            x1 = max(0, int(min(1000, (b[0] / img_w) * 1000)))
            y1 = max(0, int(min(1000, (b[1] / img_h) * 1000)))
            x2 = max(0, int(min(1000, (b[2] / img_w) * 1000)))
            y2 = max(0, int(min(1000, (b[3] / img_h) * 1000)))

            # aabb
            aabb_str = f"{x1},{y1},{x2},{y2}"

            html_content.append(f"<td rowspan=\"{cell['rowspan']}\" colspan=\"{cell['colspan']}\">{aabb_str}</td>")
        html_content.append("</tr>")

    html_content.extend(["</table>"])  # , "</body>", "</html>"
    return "\n".join(html_content)


def draw_boxes(img, boxes, color=(255, 0, 0), thickness=2):
    """在图像上绘制边界框及编号"""
    img_draw = img.copy()
    for i, box in enumerate(boxes):
        bx1, by1, bx2, by2 = map(int, box)
        cv2.rectangle(img_draw, (bx1, by1), (bx2, by2), color, thickness)
        cv2.putText(img_draw, str(i), (bx1 + 5, by1 + 15), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (0, 0, 255), 1)
    return img_draw


# ==========================================
# 流水线控制模块
# ==========================================
def process_single_image(model, img_path, output_dir, save_vis=True, align_kwargs=None):
    if align_kwargs is None: align_kwargs = {}

    img_name = Path(img_path).stem
    img_cv = cv2.imread(str(img_path))
    if img_cv is None:
        raise ValueError(f"无法读取图片: {img_path}")

    img_h, img_w = img_cv.shape[:2]

    # 1. 模型预测
    results = model.predict(source=str(img_path), verbose=False, max_det=500)
    boxes = results[0].boxes.xyxy.cpu().numpy().tolist() if results[0].boxes is not None else []

    # 2. 算法清洗与对齐
    ioa_thresh = align_kwargs.pop('ioa_thresh', 0.85)
    _ = align_kwargs.pop('abnormal_p', 0.01)

    cleaned_boxes = clean_boxes(boxes, ioa_thresh=ioa_thresh)
    preprocessed_boxes = preprocess_boxes_alignment(cleaned_boxes, **align_kwargs)

    # 3. 输出保存控制
    if save_vis:
        img_draw = draw_boxes(img_cv, preprocessed_boxes)
        (Path(output_dir) / "images").mkdir(parents=True, exist_ok=True)
        img_save_path = Path(output_dir) / "images" / f"{img_name}_vis.jpg"
        cv2.imwrite(str(img_save_path), img_draw)

    # 4. 返回生成的 HTML 字符串
    html_string = generate_html_from_aligned_boxes(preprocessed_boxes, img_w, img_h)
    return html_string


def process_json_dataset(input_json, output_json, model_path, save_vis=True, align_kwargs=None):
    print(f"Loading YOLO model from {model_path}...")
    model = YOLO(model_path)

    out_dir = Path(output_json).parent
    out_dir.mkdir(parents=True, exist_ok=True)

    with open(input_json, 'r', encoding='utf-8') as f:
        data_list = json.load(f)

    print(f"Found {len(data_list)} items in JSON. Processing...")

    generated_texts = []
    output_labels = []

    for item in tqdm(data_list, desc="Processing Images", unit="img"):
        try:
            if "images" not in item or not item["images"]:
                raise ValueError("JSON item 中缺失 images 字段或列表为空")

            img_path = Path(item["images"][0])

            html_res = process_single_image(
                model=model,
                img_path=img_path,
                output_dir=out_dir,
                save_vis=save_vis,
                align_kwargs=align_kwargs.copy() if align_kwargs else None
            )

            generated_texts.append(html_res)
            output_labels.append(1)

        except Exception as e:
            err_name = img_path.name if 'img_path' in locals() else "Unknown_Image"
            tqdm.write(f"  [ERROR] 处理 {err_name} 失败 (已生成空HTML占位): {e}")

            generated_texts.append("")
            output_labels.append(1)

    # 严格按照你要求的原始字典格式输出
    final_output = {
        "generated_texts": generated_texts,
        "output_lens": output_labels
    }

    with open(output_json, 'w', encoding='utf-8') as f:
        json.dump(final_output, f, ensure_ascii=False, indent=4)

    print(f"\n[DONE] 所有结果已保存至: {output_json}")
    print(f"--> 输入源数据条数: {len(data_list)}")
    print(f"--> 生成的结果条数: {len(generated_texts)}")


# ==========================================
# 启动入口
# ==========================================
if __name__ == "__main__":
    INPUT_JSON_PATH = "/jfs/hot/idp_datasets/deepocean_slice/table_lines/gie_exp3/test/test_20260312_095218_cell_box_with_none.json"
    OUTPUT_JSON_PATH = "/data/models/sft/MinerU2.5-2509-1.2B/gie_table_exp3/all_metrics/evaluate/deepocean/tablelines_box/yolo.json"
    MODEL_WEIGHTS = "./runs/segment/Tableline-V1/weights/best.pt"

    custom_align_params = {
        'ioa_thresh': 0.85,
        'abnormal_p': 0.01,
        'dynamic_thresh_ratio': 0.06,
        'wide_cell_ratio': 1.2,
        'edge_tol_ratio': 0.8
    }

    process_json_dataset(
        input_json=INPUT_JSON_PATH,
        output_json=OUTPUT_JSON_PATH,
        model_path=MODEL_WEIGHTS,
        save_vis=True,
        align_kwargs=custom_align_params
    )