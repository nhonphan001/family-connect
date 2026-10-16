# Sổ Bé Ngoan – App đồng hành cho con (Android) + Dashboard cho phụ huynh

Đây là bộ khung mã nguồn (starter project) cho một ứng dụng giám sát và kết nối
gia đình, gồm 2 phần:

1. **App Android cài trên máy của con** (thư mục này) – theo dõi thời gian dùng
   máy, chặn ứng dụng ngoài giờ cho phép, nhận tin nhắn/nhiệm vụ/phần thưởng từ
   ba mẹ.
2. **Dashboard web cho phụ huynh** (file `family-dashboard.jsx` gửi kèm) – xem
   thời gian sử dụng, gửi tin nhắn, đặt quy tắc, thưởng điểm.

Hai phần nói chuyện với nhau qua **Firebase Realtime Database** (miễn phí ở quy
mô gia đình, không cần tự dựng server).

## Vì sao chỉ là "khung" chứ chưa chạy được ngay

Mình không có Android Studio/máy ảo Android ở môi trường này để build và test
trực tiếp. Code dưới đây đầy đủ logic cốt lõi và biên dịch được, nhưng bạn cần:

1. Mở bằng **Android Studio** (bản mới nhất).
2. Tạo project Firebase tại https://console.firebase.google.com, bật
   **Realtime Database**, tải file `google-services.json` bỏ vào thư mục `app/`.
3. Đổi `applicationId` trong `app/build.gradle` nếu cần.
4. Build & cài vào máy của con qua cáp USB (bật "Cài đặt từ nguồn không xác định"
   nếu không public lên Play Store).

## Các quyền cần bật thủ công trên máy con (một lần duy nhất)

Android không cho phép các quyền này tự động vì lý do bảo mật — đây là điểm
*bảo vệ trẻ em khỏi bị theo dõi lén*, nên bạn cần cùng con vào Cài đặt để bật:

- **Trợ năng (Accessibility)** → bật "Sổ Bé Ngoan" (dùng để phát hiện
  ứng dụng đang mở và chặn nếu ngoài danh sách cho phép).
- **Usage Access** (Cài đặt > Ứng dụng > Truy cập đặc biệt > Truy cập sử dụng) →
  bật cho Sổ Bé Ngoan.
- **Device Admin** → xác nhận khi app hỏi lúc mở lần đầu (dùng để khoá màn hình
  khi hết giờ).
- (Tuỳ chọn) Quyền vị trí "Luôn cho phép" nếu muốn xem vị trí.

## Gợi ý về cách dùng có trách nhiệm

- Nên cài đặt **công khai, cùng con**, giải thích lý do — không cài lén. Ở tuổi
  lớp 2–lớp 5, minh bạch giúp xây dựng lòng tin thay vì cảm giác bị "rình".
- Đặt trọng tâm vào **kết nối tích cực** (nhắn tin, khen thưởng, nhiệm vụ) chứ
  không chỉ chặn/giám sát — đó cũng là lý do dashboard có phần thưởng và tin
  nhắn, không chỉ nhật ký hoạt động.

## Cấu trúc file

```
app/src/main/java/com/family/connect/
 ├─ MainActivity.kt                  # Màn hình app trên máy con: giờ còn lại, tin nhắn, điểm thưởng
 ├─ DeviceAdminReceiver.kt           # Cho phép khoá màn hình khi hết giờ
 ├─ AppMonitorAccessibilityService.kt# Phát hiện & chặn app ngoài danh sách cho phép
 ├─ UsageStatsHelper.kt              # Tính thời gian dùng từng app trong ngày
 ├─ FirebaseSyncManager.kt           # Đồng bộ 2 chiều với dashboard phụ huynh
 └─ RewardManager.kt                 # Cộng điểm thưởng khi hoàn thành nhiệm vụ
```
