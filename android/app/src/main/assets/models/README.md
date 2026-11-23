# Thư mục model AI (tuỳ chọn)

Đặt các model offline tại đây:

| File | Nguồn | Bắt buộc |
|---|---|---|
| `face_landmarker.task` | MediaPipe Face Landmarker (float16) | Không |

Tải bằng script:

```bash
./scripts/fetch-models.sh
```

## Hành vi khi thiếu model

`MediaPipeFaceEngine.isModelBundled()` trả về `false`, `VisionEngine` tự động dùng
**ML Kit Face Detection (bundled)** và thêm ghi chú vào `VisionReport.notes`:

> "Chưa có model MediaPipe (assets/models/face_landmarker.task) — dùng ML Kit."

Không có kết quả AI giả lập nào được sinh ra trong bất kỳ trường hợp nào.

## Vì sao không commit model

- Dung lượng lớn (~3 MB) không phù hợp để commit lặp lại.
- Model có thể thay đổi theo phiên bản; CI nên tải và kiểm tra checksum.
- `.gitignore` đã chặn `*.task` và `*.tflite` trong thư mục này.
