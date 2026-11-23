# Cài đặt & vận hành

## 1. Cài APK lên điện thoại

1. Tải `vcam-pro-ultimate.apk` từ trang `website/download.html` (hoặc GitHub Releases).
2. Kiểm tra SHA-256:
   ```bash
   sha256sum vcam-pro-ultimate.apk
   ```
3. Bật "Cài đặt từ nguồn không xác định" cho trình quản lý file / trình duyệt.
4. Mở file APK và cài đặt.
5. Cấp quyền Camera (bắt buộc) và Micro (tuỳ chọn, cho video có tiếng).

Qua ADB:

```bash
adb install -r vcam-pro-ultimate.apk
```

> Bản debug có `applicationId = com.vcampro.ultimate.debug` nên cài song song với bản release.

## 2. Build từ mã nguồn

```bash
cd android
./gradlew :app:assembleDebug
```

Nếu wrapper chưa chạy được trên máy mới:

```bash
cd android
gradle wrapper --gradle-version 8.11.1
```

Mở bằng Android Studio: `File → Open → android/`.

## 3. Cấu hình ký release

Tạo keystore (một lần):

```bash
keytool -genkeypair -v -keystore vcam-release.jks -keyalg RSA \
  -keysize 2048 -validity 10000 -alias vcam
```

Build release có ký:

```bash
export VCAM_KEYSTORE_PATH=$PWD/vcam-release.jks
export VCAM_KEYSTORE_PASSWORD='...'
export VCAM_KEY_ALIAS=vcam
export VCAM_KEY_PASSWORD='...'
./gradlew :app:assembleRelease
```

Xác minh chữ ký:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

**Không bao giờ commit file `.jks`.** `.gitignore` đã chặn `*.jks`, `*.keystore`, `keystore.properties`.

## 4. Secrets cho GitHub Actions

Trong repository → Settings → Secrets and variables → Actions:

| Secret | Nội dung |
|---|---|
| `VCAM_KEYSTORE_BASE64` | `base64 -w0 vcam-release.jks` |
| `VCAM_KEYSTORE_PASSWORD` | mật khẩu keystore |
| `VCAM_KEY_ALIAS` | alias (ví dụ `vcam`) |
| `VCAM_KEY_PASSWORD` | mật khẩu key |

Nếu chưa có các secret này, workflow release vẫn build được nhưng APK ký bằng key debug —
chỉ dùng để thử nghiệm.

## 5. Bật GitHub Pages

Settings → Pages → Source: **GitHub Actions**.
Sau lần push đầu tiên vào `main`, workflow `pages.yml` sẽ xuất bản thư mục `website/`.

URL dự kiến: `https://<user>.github.io/<repo>/`
Trang tải: `https://<user>.github.io/<repo>/download.html`

## 6. Chạy website local

```bash
python3 -m http.server -d website 8080
# mở http://localhost:8080
```

Website đọc `website/data/release.json`; nếu chưa có bản phát hành, nút tải bị vô hiệu hoá
và hiển thị thông báo — không có link giả.

## 7. Model AI tuỳ chọn

```bash
./scripts/fetch-models.sh
```

Tải `face_landmarker.task` và kiểm tra checksum trước khi đặt vào
`android/app/src/main/assets/models/`. Không bắt buộc: thiếu model thì AI Vision dùng ML Kit.

## 8. Kiểm thử chế độ máy bay

1. Bật Airplane Mode (tắt Wi-Fi, dữ liệu di động, không SIM).
2. Mở ứng dụng → Onboarding → Dashboard.
3. Chụp ảnh, quay video, tạo hồ sơ, phân tích AI, chỉnh ảnh, xuất video, mở lại thư viện.
4. Tắt ứng dụng, khởi động lại máy, mở lại → dữ liệu phải còn nguyên.

Nếu bất kỳ bước nào cần Internet, đó là lỗi kiến trúc: ứng dụng không có quyền INTERNET,
nên mọi trường hợp như vậy đều xuất phát từ việc thêm SDK mạng vào dependency.
