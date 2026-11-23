# Báo cáo kiểm thử — VCAM PRO ULTIMATE VIP 2026

Tài liệu này ghi lại **trung thực** những gì đã kiểm thử và những gì chưa.
Không có mục nào được đánh dấu "passed" nếu chưa thực sự chạy.

## 1. Trạng thái tổng hợp

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| Unit test (JVM) | **Đã viết — CHƯA CHẠY** | Không có JDK/Android SDK trên thiết bị tạo mã nguồn |
| Lint | **Chưa chạy** | Sẽ chạy trong `android-build.yml` |
| Build debug APK | **Chưa chạy** | Sẽ chạy trên GitHub Actions |
| Build release APK | **Chưa chạy** | Cần keystore + runner có Android SDK |
| Instrumented test | **Đã viết — CHƯA CHẠY** | Cần emulator hoặc thiết bị thật |
| Test trên thiết bị thật | **Chưa thực hiện** | — |
| Test Airplane Mode | **Chưa thực hiện** | Quy trình có trong `docs/INSTALLATION.md` mục 8 |
| Xác minh chữ ký APK | **Chưa chạy** | `apksigner verify` được gọi trong workflow release |

Kết luận: **chưa có bằng chứng build/test thực tế** tại thời điểm bàn giao mã nguồn này.
Bước tiếp theo bắt buộc là chạy CI (`android-build.yml`) để có kết quả thật.

## 2. Unit test đã viết

| File | Nội dung | Số test |
|---|---|---|
| `core/common/FormattersTest.kt` | định dạng dung lượng/thời lượng/thời gian theo tiếng Việt, hex, tên file | 6 |
| `core/vision/QualityMathTest.kt` | luminance Rec.601, phương sai Laplacian, điểm sáng/tương phản/phơi sáng, điểm tổng hợp, verdict | 11 |
| `core/security/PinHasherTest.kt` | PBKDF2 salt ngẫu nhiên, verify đúng/sai, hash không chứa plaintext | 4 |

Chạy:

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

Báo cáo: `android/app/build/reports/tests/testDebugUnitTest/index.html`.

## 3. Instrumented test đã viết

`androidTest/java/com/vcampro/ultimate/OfflineGuaranteeTest.kt`

| Test | Kỳ vọng |
|---|---|
| `manifest_doesNotRequestInternetPermission` | không có `INTERNET`, không có `ACCESS_NETWORK_STATE` |
| `manifest_requestsCameraPermission` | có `CAMERA` |
| `packageName_isVcamPro` | package bắt đầu bằng `com.vcampro.ultimate` |

Chạy:

```bash
cd android && ./gradlew :app:connectedDebugAndroidTest
```

## 4. Kịch bản kiểm thử thủ công (chưa thực hiện)

### 4.1 Khởi động
- [ ] Cài đặt APK thành công trên Android 8, 10, 13, 14
- [ ] Splash → Onboarding → Dashboard không crash
- [ ] Dashboard hiển thị 0 ảnh / 0 video / 0 hồ sơ khi database trống

### 4.2 Camera Studio
- [ ] Cấp quyền camera, preview hiển thị
- [ ] Chụp ảnh → file xuất hiện trong thư viện
- [ ] Quay video → file có thời lượng > 0
- [ ] Chuyển camera trước/sau
- [ ] Zoom, torch, chạm lấy nét
- [ ] Hẹn giờ 3s/10s
- [ ] Pause/Resume khi quay (nếu thiết bị hỗ trợ)

### 4.3 AI Vision
- [ ] Phân tích ảnh có 1 khuôn mặt → faceCount = 1
- [ ] Phân tích ảnh không có người → faceCount = 0, không crash
- [ ] Đổi 3 chế độ hiệu năng → thời gian xử lý khác nhau
- [ ] Kết quả được lưu vào bảng `ai_analysis`
- [ ] Bật Airplane Mode → phân tích vẫn chạy

### 4.4 Photo Studio
- [ ] Undo/Redo nhiều bước
- [ ] So sánh trước/sau
- [ ] Xuất file mới, file gốc không đổi
- [ ] Xuất khi bộ nhớ gần đầy → báo lỗi rõ ràng

### 4.5 Video Studio
- [ ] Xem trước bằng ExoPlayer
- [ ] Trim 3 giây → file xuất có thời lượng ~3 giây
- [ ] Nén 720p → độ phân giải giảm
- [ ] Huỷ giữa chừng không để lại file rác

### 4.6 Database & bền vững
- [ ] Tắt app, mở lại → dữ liệu còn
- [ ] Khởi động lại máy → dữ liệu còn
- [ ] Nâng cấp DB từ v1 → v2 không mất dữ liệu

### 4.7 Bảo mật
- [ ] Đặt PIN → khoá khi mở lại sau 30 giây
- [ ] Sai PIN 5 lần → thông báo số lần sai
- [ ] Bật sinh trắc học → mở khoá bằng vân tay
- [ ] Xoá toàn bộ dữ liệu → thư viện trống, DB trống

## 5. Giới hạn đã biết (không phải lỗi)

| Hạng mục | Chi tiết |
|---|---|
| Speed change video | Media3 Transformer 1.4 không có speed effect dựng sẵn → không có UI |
| Exposure EV | Chỉ bật khi `CONTROL_AE_COMPENSATION_RANGE` khác 0 |
| Pause tác vụ nền | WorkManager không hỗ trợ pause thật → huỷ + chạy lại |
| Occlusion detection | Heuristic từ số landmark/contour, không phải model riêng |
| Secure delete | Best-effort, không bảo đảm tuyệt đối trên flash |
| MediaPipe | Cần `assets/models/face_landmarker.task`; thiếu thì fallback ML Kit |

## 6. Cách tạo bằng chứng kiểm thử thật

1. Push repository lên GitHub.
2. Mở tab Actions → workflow **Android CI**.
3. Tải artifact `vcam-pro-debug-apk` và `reports`.
4. Chụp lại kết quả (số test passed/failed) và cập nhật bảng mục 1.
5. Cài APK lên thiết bị thật, chạy mục 4, đánh dấu từng ô.

Chỉ sau bước 4 và 5 mới được coi là "đã kiểm thử".
