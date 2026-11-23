# VCAM PRO ULTIMATE VIP 2026

Ứng dụng **Android native** chạy hoàn toàn trên thiết bị: Camera Studio, AI Vision offline,
Photo Studio, Video Studio, Media Library, Processing Center, Device Center và Security Center.

> **ONE DEVICE. ONE APP. FULL CONTROL. ZERO VPS.**

---

## 1. Nguyên tắc kiến trúc

Ứng dụng hoạt động độc lập 100% trên từng điện thoại Android.

| Hạng mục | Trạng thái |
|---|---|
| VPS | **Không cần** |
| Backend / API server | **Không có** |
| Database server (PostgreSQL, Redis) | **Không có** |
| Firebase / Supabase | **Không dùng** |
| Cloud AI / API AI trả phí | **Không dùng** |
| Đăng nhập / kích hoạt online | **Không có** |
| Quyền `android.permission.INTERNET` | **Không khai báo** |

Điểm quan trọng nhất: `AndroidManifest.xml` **không** khai báo quyền `INTERNET`.
Ở mức hệ điều hành, ứng dụng không thể mở socket ra mạng — đây là bằng chứng kỹ thuật
chứ không phải lời hứa. Có một bài kiểm thử instrumented (`OfflineGuaranteeTest`) khẳng định điều này.

## 2. Cấu trúc repository

```
vcam-pro-ultimate/
├── android/                     # Gradle project (Android app)
│   ├── app/
│   │   ├── build.gradle.kts
│   │   ├── proguard-rules.pro
│   │   └── src/
│   │       ├── main/
│   │       │   ├── AndroidManifest.xml
│   │       │   ├── java/com/vcampro/ultimate/
│   │       │   │   ├── VcamApp.kt
│   │       │   │   ├── MainActivity.kt
│   │       │   │   ├── core/
│   │       │   │   │   ├── common/      # hằng số, formatter tiếng Việt
│   │       │   │   │   ├── data/        # Room entities, DAO, repository
│   │       │   │   │   ├── device/      # thông tin phần cứng
│   │       │   │   │   ├── media/       # FileStore, ImageIO
│   │       │   │   │   ├── prefs/       # DataStore settings
│   │       │   │   │   ├── security/    # Keystore, PIN, lock
│   │       │   │   │   ├── ui/          # theme, components, navigation
│   │       │   │   │   └── vision/      # AI engine, quality math
│   │       │   │   └── features/        # 14 màn hình
│   │       │   └── res/
│   │       ├── test/                    # unit test (JUnit, chạy trên JVM)
│   │       └── androidTest/             # instrumented test
│   ├── gradle/libs.versions.toml
│   ├── gradlew / gradlew.bat
│   └── settings.gradle.kts
├── website/                     # Website tĩnh (GitHub Pages), không build step
├── docs/                        # kiến trúc, cài đặt, test, privacy
├── scripts/                     # tải model AI, tiện ích build
├── .github/workflows/           # CI build, release APK, deploy website
├── CHANGELOG.md
└── README.md
```

## 3. Công nghệ

- **Kotlin 2.0.21**, AGP 8.7.2, Gradle 8.11.1, JDK 17
- **Jetpack Compose** + Material 3 (Neon Vision Interface)
- **CameraX 1.4** (Preview, ImageCapture, VideoCapture + Recorder)
- **Android Media3 1.4** (ExoPlayer, Transformer, Effect)
- **ML Kit Face Detection (bundled)** — model nằm trong APK, không cần Play Services
- **MediaPipe Tasks Vision** — tuỳ chọn, dùng khi có `assets/models/face_landmarker.task`
- **Room 2.6** (SQLite), **DataStore**, **WorkManager**, **Hilt**, **Android Keystore**

## 4. Build

### Yêu cầu môi trường
- JDK 17
- Android SDK 35 (compileSdk 35), minSdk 26
- Gradle 8.11.1 (đã có wrapper)

### Lệnh

```bash
cd android
./gradlew :app:assembleDebug      # APK debug
./gradlew :app:testDebugUnitTest  # unit test
./gradlew :app:lintDebug          # lint
./gradlew :app:assembleRelease    # APK release (cần keystore để ký thật)
```

APK đầu ra: `android/app/build/outputs/apk/debug/app-debug.apk`.

### Ký release

Đặt biến môi trường trước khi build:

```bash
export VCAM_KEYSTORE_PATH=/duong/dan/vcam-release.jks
export VCAM_KEYSTORE_PASSWORD=...
export VCAM_KEY_ALIAS=vcam
export VCAM_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Nếu không có keystore, `assembleRelease` vẫn chạy nhưng ký bằng key debug
(chỉ dùng để thử nghiệm, **không** phát hành stable).

### Model AI tuỳ chọn

```bash
./scripts/fetch-models.sh
```

Script tải `face_landmarker.task` từ nguồn chính thức của MediaPipe, kiểm tra SHA-256
rồi đặt vào `android/app/src/main/assets/models/`. Không có model này, AI Vision vẫn
hoạt động đầy đủ bằng ML Kit bundled và ghi chú rõ trong báo cáo phân tích.

## 5. Website

Thư mục `website/` là site tĩnh thuần HTML/CSS/JS, không cần build.

- Chạy local: mở `website/index.html` hoặc `python3 -m http.server -d website 8080`
- Deploy: workflow `.github/workflows/pages.yml` xuất bản thư mục `website/` lên GitHub Pages
- Dữ liệu phát hành: `website/data/release.json` — được CI cập nhật tự động mỗi lần release

## 6. Phát hành APK

1. Cấu hình secrets trong GitHub repository:
   `VCAM_KEYSTORE_BASE64`, `VCAM_KEYSTORE_PASSWORD`, `VCAM_KEY_ALIAS`, `VCAM_KEY_PASSWORD`
2. Gắn tag: `git tag v1.0.0 && git push origin v1.0.0`
3. Workflow `android-release.yml` sẽ: chạy test → build → ký → xác minh chữ ký bằng
   `apksigner` → tính SHA-256 → tạo GitHub Release → cập nhật `release.json`.

## 7. Trạng thái kiểm thử

Xem [docs/TESTING.md](docs/TESTING.md). Tóm tắt trung thực:

- Unit test **đã viết** (Formatters, QualityMath, PinHasher) nhưng **chưa chạy** trong môi trường
  tạo mã nguồn này (không có Android SDK/JDK trên thiết bị tạo mã).
- Chưa build APK tại máy local; build được thực hiện trên GitHub Actions.
- Chưa test trên thiết bị Android thật.

Không có tuyên bố "đã test" nào trong tài liệu này nếu chưa thực sự chạy.

## 8. Giới hạn

- Đổi tốc độ video chưa hỗ trợ (Media3 Transformer không có speed effect dựng sẵn).
- Phơi sáng thủ công phụ thuộc khả năng AE compensation của thiết bị.
- Tạm dừng tác vụ nền = huỷ + chạy lại (WorkManager không hỗ trợ pause thật).
- Phát hiện che khuất là heuristic từ landmark, không phải model chuyên dụng.
- Xoá an toàn là best-effort trên flash storage.

## 9. Sử dụng có trách nhiệm

VCAM PRO **không** cung cấp camera ảo thay thế luồng camera của ứng dụng khác,
**không** giả mạo danh tính, **không** vượt qua xác thực sinh trắc học hay kiểm tra liveness,
và **không** can thiệp vào ứng dụng ngân hàng. Chỉ sử dụng hình ảnh của chính bạn
hoặc khi đã có sự đồng ý của người xuất hiện trong ảnh/video.

## 10. Tài liệu

- [Kiến trúc](docs/ARCHITECTURE.md)
- [Cài đặt & kiểm thử](docs/INSTALLATION.md)
- [Báo cáo kiểm thử](docs/TESTING.md)
- [Chính sách riêng tư](docs/PRIVACY.md)
- [Lịch sử phiên bản](CHANGELOG.md)
