# Lịch sử phiên bản

## Kênh tải APK

| Kênh | Trạng thái | Ghi chú |
|---|---|---|
| GitHub Releases (`latest-apk`) | APK debug, tự động | Tạo bởi `android-build.yml` mỗi khi build trên `main` thành công. Ký bằng debug key — chỉ để cài thử. |
| GitHub Releases (`v*`) | Bản stable | Chỉ tạo khi có keystore riêng và gắn tag phiên bản. |

Link tải trực tiếp (không cần đăng nhập):

```
https://github.com/MPV999/vcam-pro-ultimate/releases/download/latest-apk/vcam-pro-ultimate.apk
```

`website/data/release.json` được CI cập nhật sau mỗi lần build xanh, nên trang
`download.html` luôn trỏ tới file APK có thật.

## v1.0.0 — Bản đầu tiên (chưa phát hành stable)

### Android app
- **Dashboard**: số liệu thật từ Room (ảnh, video, hồ sơ, tác vụ, dung lượng), biểu đồ 7 ngày,
  nhật ký hoạt động, trạng thái AI/camera/offline.
- **Camera Studio**: CameraX, camera trước/sau, chụp ảnh, quay video, zoom, torch,
  chạm lấy nét, hẹn giờ, lưới 3×3, phơi sáng EV (khi phần cứng hỗ trợ), pause/resume.
- **Face Profile Manager**: tạo/đổi tên/xoá hồ sơ, phân nhóm, mô tả, consent record,
  đếm media, xem media theo hồ sơ.
- **AI Vision Engine**: ML Kit bundled (offline) + MediaPipe tuỳ chọn, landmark,
  tư thế đầu, ước lượng che khuất, điểm chất lượng ảnh, 3 chế độ hiệu năng.
- **Video Analysis**: trích frame, phát hiện khuôn mặt theo frame, thống kê.
- **Photo Studio**: cắt, xoay, lật, resize, sáng/tương phản/bão hoà, 7 bộ lọc, watermark,
  undo/redo, so sánh trước–sau, xuất không ghi đè file gốc.
- **Video Studio**: ExoPlayer preview, trim, xoay, lật, tắt tiếng, nén 720p,
  xuất H.264+AAC bằng Media3 Transformer, trích frame.
- **Media Library**: tìm kiếm, lọc, chọn hàng loạt, yêu thích, đổi tên, xoá an toàn, import SAF.
- **Processing Center**: hàng đợi WorkManager, tiến độ, pause/resume/cancel/retry, lịch sử.
- **Device Center**: phần cứng, camera, codec, pin, kiểm tra tương thích, chế độ đề xuất.
- **Security Center**: Keystore, PIN PBKDF2, sinh trắc học, khoá phiên, kiểm tra toàn vẹn,
  xoá file mồ côi, xoá toàn bộ dữ liệu, privacy dashboard.
- **Settings**: dark/light, hiệu năng, camera, watermark, sao lưu/khôi phục local.
- **About**: phiên bản, kiến trúc local-first, giới hạn đã biết, giấy phép.

### Hạ tầng
- GitHub Actions: `android-build.yml` (lint, test, debug APK), `android-release.yml`
  (build, ký, SHA-256, GitHub Release, cập nhật metadata website), `pages.yml` (deploy website).
- Website tĩnh 8 trang: Home, Features, Download, Changelog, Installation,
  System Requirements, Privacy, About.
- Không có VPS, backend, database server hay cloud AI.

### Đã biết
- Chưa build/test trong môi trường tạo mã nguồn (xem `docs/TESTING.md`).
- Speed change cho video chưa hỗ trợ.
- MediaPipe cần model tuỳ chọn trong `assets/models/`.
