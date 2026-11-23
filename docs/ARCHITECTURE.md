# Kiến trúc — VCAM PRO ULTIMATE VIP 2026

## 1. Tổng quan

Ứng dụng một module Gradle (`:app`) với phân lớp theo package, phản ánh đúng cấu trúc
`core/` + `features/` mà không phải trả giá bằng việc chia quá nhiều module Gradle
(mỗi module thêm build file, dependency graph và thời gian cấu hình).

```
com.vcampro.ultimate
├── VcamApp                      Application + Hilt + WorkManager Configuration
├── MainActivity                 FragmentActivity, theme, lock gate, NavHost
├── core
│   ├── common                   AppConstants, Formatters (tiếng Việt), AppUtils
│   ├── data                     Entities, DAOs, VcamDatabase, Repositories
│   ├── device                   DeviceInfoProvider (RAM, camera, codec, pin)
│   ├── media                    FileStore (app-private storage), ImageIO
│   ├── prefs                    SettingsRepository (DataStore)
│   ├── security                 CryptoManager, PinHasher, LockManager
│   ├── ui                       Theme, Components, Navigation
│   └── vision                   QualityMath, ImageQualityAnalyzer, ML Kit, MediaPipe,
│                                VisionEngine, VideoAnalyzer
└── features                     splash, onboarding, dashboard, camera, faceprofile,
                                 aivision, photostudio, videostudio, medialibrary,
                                 processing, devicecenter, security, settings, about
```

## 2. Luồng dữ liệu

```
UI (Compose)  ──►  ViewModel (StateFlow / mutableStateOf)  ──►  Repository  ──►  Room DAO
     ▲                                                                              │
     └──────────────────────── Flow<List<Entity>> / StateFlow ◄─────────────────────┘
```

- Không có lớp network. Không có Retrofit/OkHttp trong dependency graph.
- File media nằm trong `context.filesDir` (app-private). Đăng ký vào bảng `media_files`.
- Thumbnail được tạo bằng `ImageIO.createThumbnail` / `videoThumbnail` khi thêm media.

## 3. Database

Room, `version = 2`, `exportSchema = true`, schema ghi vào `android/app/schemas/`.

| Bảng | Mục đích |
|---|---|
| `face_profiles` | hồ sơ khuôn mặt (UID, tên, nhóm, thumbnail, consent) |
| `media_files` | ảnh/video local (đường dẫn, kích thước, thumbnail, favorite) |
| `albums` | album tuỳ chỉnh |
| `processing_tasks` | hàng đợi tác vụ, tiến độ, lỗi |
| `ai_analysis` | kết quả phân tích AI |
| `app_settings` | key/value bổ sung |
| `activity_logs` | nhật ký hoạt động local |
| `export_history` | lịch sử xuất file + SHA-256 |

Migration mẫu `MIGRATION_1_2` thêm cột `lastOpenedAt` vào `media_files`.
**Không** dùng `fallbackToDestructiveMigration` — dữ liệu người dùng không bị xoá khi nâng cấp.

## 4. AI Vision Engine

```
VisionEngine.analyze(bitmap, mode)
   ├── ImageQualityAnalyzer   → QualityReport (sharpness/brightness/contrast/exposure/resolution)
   ├── MediaPipeFaceEngine    → nếu mode = PERFORMANCE và có assets/models/face_landmarker.task
   └── MlKitFaceEngine        → mặc định, model bundled trong APK
```

- `QualityMath` là object thuần Kotlin (không phụ thuộc Android) nên **unit-test được trên JVM**:
  phương sai Laplacian, trung bình/độ lệch chuẩn độ chói, tỉ lệ pixel cháy sáng, điểm tổng hợp có trọng số.
- Không có nhánh sinh kết quả giả. Nếu MediaPipe không có model, hệ thống chuyển sang ML Kit
  và **ghi chú rõ trong `VisionReport.notes`**.
- `VideoAnalyzer` trích frame bằng `MediaMetadataRetriever`, chạy face detection từng frame,
  tổng hợp tỉ lệ frame có mặt + chất lượng trung bình.

### Ba chế độ hiệu năng

| Chế độ | Ảnh phân tích | Frame video | Khuôn mặt tối đa |
|---|---|---|---|
| ECONOMY | 640 px | 1/3 | 3 |
| BALANCED | 1024 px | chuẩn | 5 |
| PERFORMANCE | 1920 px | ×2 | 10 |

## 5. Camera

`CameraController` bọc CameraX:

- `Preview` + `ImageCapture` + `VideoCapture<Recorder>` bind cùng lifecycle.
- Zoom qua `CameraControl.setZoomRatio`, torch qua `enableTorch`,
  lấy nét qua `FocusMeteringAction`.
- Phơi sáng qua `Camera2CameraControl.setCaptureRequestOptions(CONTROL_AE_EXPOSURE_COMPENSATION)`
  và chỉ bật UI khi `CONTROL_AE_COMPENSATION_RANGE` cho phép.
- Ghi video qua `Recorder` + `FileOutputOptions`, hỗ trợ pause/resume nếu thiết bị cho phép.

## 6. Bảo mật

- `CryptoManager`: AES-256-GCM, khoá trong Android Keystore (không export được).
- `PinHasher`: PBKDF2-HMAC-SHA256, 120.000 vòng, salt 16 byte ngẫu nhiên.
- `LockManager`: khoá phiên (30 giây ở background), PIN, sinh trắc học qua `BiometricPrompt`.
- Manifest: `allowBackup=false`, `dataExtractionRules` loại trừ database/sharedpref khỏi cloud backup.
- `FileStore.secureDelete`: ghi đè byte ngẫu nhiên (giới hạn 64 MB) rồi xoá — best-effort.

## 7. Tác vụ nền

`ProcessingWorker` (`@HiltWorker`) chạy `CoroutineWorker`:

- `AI_ANALYSIS` — phân tích 1 ảnh
- `VIDEO_ANALYSIS` — phân tích frame video
- `BATCH_EXPORT` — resize + nén lại toàn bộ ảnh, ghi vào thư mục export

`TaskScheduler` dùng `enqueueUniqueWork` với tag `vcam_task_<id>` để đảm bảo mỗi tác vụ
chỉ có một instance. "Tạm dừng" = cancel + đánh dấu `PAUSED`; "Tiếp tục" = enqueue lại.

## 8. Quyết định thiết kế

| Quyết định | Lý do |
|---|---|
| Một module Gradle, nhiều package | Giảm thời gian build/cấu hình, không mất khả năng tách lớp; dễ tách thành multi-module sau này |
| Không khai báo quyền INTERNET | Bảo đảm offline ở mức OS, không thể vô tình thêm SDK gọi mạng |
| ML Kit bundled thay vì Play Services | Hoạt động trên máy không có GMS, không cần tải model lúc chạy |
| MediaPipe là tuỳ chọn | Model ~3 MB không commit vào repo; thiếu model vẫn có engine dự phòng |
| Không dùng `fallbackToDestructiveMigration` | Bảo toàn dữ liệu người dùng qua các bản cập nhật |
| `mutableStateOf` cho state cục bộ của màn hình | Ít boilerplate hơn StateFlow cho state chỉ sống trong 1 màn hình |
