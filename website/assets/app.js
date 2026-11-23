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

  function statusLabel(status) {
    if (status === "stable") return "Stable";
    if (status === "debug") return "Bản thử nghiệm (debug)";
    return "Chưa phát hành";
  }

  function applyRelease(data) {
    var status = (data && data.status) || "pending";
    var hasDownload = !!(data && data.downloadUrl && (status === "stable" || status === "debug"));
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
      setText("[data-release='status']", statusLabel(status));
    }

    if (hasDownload) {
      if (button) {
        button.setAttribute("href", data.downloadUrl);
        button.setAttribute("aria-disabled", "false");
        button.setAttribute("target", "_blank");
        button.setAttribute("rel", "noopener");
        button.textContent = "DOWNLOAD VCAM PRO APK";
      }
      if (hashButton && data.sha256Url) {
        hashButton.setAttribute("href", data.sha256Url);
        hashButton.style.display = "inline-flex";
      }
      if (notice) {
        if (status === "stable") {
          notice.className = "notice ok";
          notice.textContent =
            "Bản phát hành stable đã sẵn sàng. File APK tải trực tiếp từ GitHub Releases, không cần đăng nhập.";
        } else {
          notice.className = "notice";
          notice.textContent =
            "Đây là APK thử nghiệm do GitHub Actions build tự động và ký bằng debug key. " +
            "Cài được trên máy thật để trải nghiệm, nhưng KHÔNG phải bản phát hành stable.";
        }
      }
    } else {
      if (button) {
        button.setAttribute("href", "#");
        button.setAttribute("aria-disabled", "true");
        button.textContent = "CHƯA CÓ BẢN PHÁT HÀNH";
      }
      if (hashButton) hashButton.style.display = "none";
      if (notice) {
        notice.className = "notice";
        notice.textContent =
          "Chưa có file APK nào được phát hành. GitHub Actions sẽ tự tạo bản build thử nghiệm " +
          "ngay khi build thành công trên nhánh main — sau đó trang này tự cập nhật link tải thật.";
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
