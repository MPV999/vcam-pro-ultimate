# Báo cáo kiểm thử — VCAM PRO ULTIMATE VIP 2026

Tài liệu này ghi lại **trung thực** những gì đã kiểm thử và những gì chưa.
Không có mục nào được đánh dấu "passed" nếu chưa thực sự chạy.

## 1. Trạng thái tổng hợp

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| CI `android-build.yml` | **Đã chạy thật** (GitHub Actions) | Lần 1–2: FAIL ở bước KSP — xem mục 1.1 |
| Sửa lỗi từ CI | **Đã sửa** (`02e1e6a`) | index `export_history` dùng `exportedAt` |
| Unit test (JVM) | Đã viết — chờ CI xác nhận | 21 test trong `app/src/test` |
| Lint | Chờ CI xác nhận | `abortOnError = false` |
| Build debug APK | Chờ CI xác nhận | CI tự phát hành APK thử nghiệm khi xanh |
| Instrumented test | Đã viết — **chưa chạy** | Cần emulator hoặc thiết bị thật |
| Test trên thiết bị thật | **Chưa thực hiện** | — |
| Test Airplane Mode | **Chưa thực hiện** | Quy trình: `docs/INSTALLATION.md` mục 8 |

### 1.1 Bằng chứng CI thật (không phải suy đoán)

Lần chạy đầu tiên của `Android CI` (commit `8b81ab1`) thất bại sau 2m22s.
Vì log GitHub Actions yêu cầu đăng nhập, workflow được bổ sung bước đẩy lỗi Kotlin/KSP
thành annotation. Lần chạy thứ hai (commit `cc740fa`) cho ra lỗi thật đầu tiên:

```
e: [ksp] .../core/data/Entities.kt:169: createdAt referenced in the index does not exists
   in the Entity. Available column names: id, mediaId, outputPath, format, sizeBytes,
   checksum, exportedAt
```

Đã sửa: `@Entity(tableName = "export_history", indices = [Index("exportedAt")])`.

Đây là bằng chứng cho thấy: (a) pipeline build thật sự chạy, (b) cơ chế báo lỗi hoạt động,
(c) các lỗi còn lại sẽ tiếp tục được phát hiện theo cùng cách cho tới khi build xanh.

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
