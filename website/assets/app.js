/* VCAM PRO — website tĩnh, không backend. */
(function () {
  "use strict";

  function formatBytes(bytes) {
    var value = Number(bytes) || 0;
    if (value <= 0) return "Chưa có dữ liệu";
    if (value < 1024) return value + " B";
    var units = ["KB", "MB", "GB"];
    var index = -1;
    do {
      value = value / 1024;
      index++;
    } while (value >= 1024 && index < units.length - 1);
    return value.toFixed(1).replace(".", ",") + " " + units[index];
  }

  function setText(selector, text) {
    document.querySelectorAll(selector).forEach(function (node) {
      node.textContent = text;
    });
  }

  function initNav() {
    var toggle = document.querySelector(".nav-toggle");
    var links = document.querySelector(".nav-links");
    if (!toggle || !links) return;
    toggle.addEventListener("click", function () {
      links.classList.toggle("open");
    });
  }

  function initCopy() {
    document.querySelectorAll("[data-copy]").forEach(function (button) {
      button.addEventListener("click", function () {
        var target = document.querySelector(button.getAttribute("data-copy"));
        if (!target) return;
        var text = target.textContent.trim();
        if (navigator.clipboard) {
          navigator.clipboard.writeText(text).then(function () {
            button.textContent = "Đã copy";
            setTimeout(function () {
              button.textContent = "Copy";
            }, 1800);
          });
        }
      });
    });
  }

  function applyRelease(data) {
    var hasStable = data && data.status === "stable" && data.downloadUrl;
    var notice = document.getElementById("release-notice");
    var button = document.getElementById("download-button");
    var hashButton = document.getElementById("sha256-button");

    if (data) {
      setText("[data-release='version']", data.version || "—");
      setText("[data-release='build']", data.buildNumber ? String(data.buildNumber) : "—");
      setText("[data-release='date']", data.releaseDate || "—");
      setText("[data-release='size']", formatBytes(data.apkSizeBytes));
      setText("[data-release='minAndroid']", data.minAndroid || "Android 8.0 (API 26)");
      setText(
        "[data-release='arch']",
        (data.architectures || []).join(" · ") || "arm64-v8a"
      );
      setText("[data-release='sha256']", data.sha256 || "Sẽ được công bố khi phát hành");
      setText("[data-release='status']", hasStable ? "Stable" : "Chưa phát hành");
    }

    if (hasStable) {
      if (button) {
        button.setAttribute("href", data.downloadUrl);
        button.setAttribute("aria-disabled", "false");
        button.textContent = "DOWNLOAD VCAM PRO APK";
      }
      if (hashButton && data.sha256Url) {
        hashButton.setAttribute("href", data.sha256Url);
        hashButton.style.display = "inline-flex";
      }
      if (notice) {
        notice.className = "notice ok";
        notice.textContent =
          "Bản phát hành stable đã sẵn sàng. File APK được phân phối trực tiếp từ GitHub Releases, không cần đăng nhập.";
      }
    } else {
      if (button) {
        button.setAttribute("href", "#");
        button.setAttribute("aria-disabled", "true");
        button.textContent = "CHƯA CÓ BẢN PHÁT HÀNH STABLE";
      }
      if (hashButton) hashButton.style.display = "none";
      if (notice) {
        notice.className = "notice";
        notice.textContent =
          "Chưa có bản phát hành stable. APK sẽ được tạo tự động bởi GitHub Actions khi repository được gắn tag phiên bản (ví dụ v1.0.0). Không có link tải giả trên trang này.";
      }
    }
  }

  function loadRelease() {
    fetch("data/release.json", { cache: "no-store" })
      .then(function (response) {
        if (!response.ok) throw new Error("Không đọc được metadata");
        return response.json();
      })
      .then(applyRelease)
      .catch(function () {
        applyRelease(null);
      });
  }

  document.addEventListener("DOMContentLoaded", function () {
    initNav();
    initCopy();
    loadRelease();
  });
})();
