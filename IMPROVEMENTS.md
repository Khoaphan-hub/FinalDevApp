# Journify — Rà soát sau khi gộp nhánh

**Ngày:** 03/09/2026 · **Deadline:** 23:59 ngày 05/09/2026 · **Nhánh:** `Khai` @ `eee5575`
**Quy mô:** 65 file Java / 6.035 dòng · 6 file test / 25 test · 247 string vi, 223 en · backend gọi 11 endpoint

Rà lại từ đầu trên codebase đã gộp (đăng nhập, hồ sơ, cộng đồng, chế độ offline, cache ảnh đĩa).

🔴 **P0** chặn nộp bài hoặc lỗi thật · 🟡 **P1** ảnh hưởng điểm · 🟢 **P2** nếu dư thời gian

---

## 0. Đã xử lý (cập nhật 03/09)

| Mục | Commit |
|---|---|
| 4.1 Khoá nối ma trận khoảng cách — khớp 0/242 → **242/242** | `94bdfea` |
| 4.2 Gộp kết nối SQLite — 54 → **0** mỗi request | `0a2dcfc` |
| 4.3 N+1 → `in_bulk` — 26 → **10** truy vấn | `e93ca8e` |
| 4.4 32 `print()` → `logging` + cấu hình `LOGGING` | `9b2cf43` |
| 6 Cấu hình ký APK, base URL cho release, `applicationId` | `9fd1620` |

Endpoint tạo lịch trình: **133 ms → 83 ms**. Quan trọng hơn, tuyến đường giờ mới thực sự
dùng khoảng cách đường bộ thay vì đường chim bay.

Phát hiện thêm khi build release (đã sửa trong `9fd1620`): `sample_poi.png` thực chất là
**WebP** và `sample_eatery.png` là **JPEG**. Build debug bỏ qua, build release nén PNG nên
chết. Cả nhóm chưa ai từng chạy `assembleRelease` nên chưa gặp.

### Phát sinh mới — chế độ offline không dùng được trên máy vừa cài

Chuỗi fallback là `remote → PlaceCache → DemoPlannerRepository`. Nhưng `PlaceCache` **rỗng
trên bản cài mới**, và không có dữ liệu nào đóng gói sẵn trong `assets/`.

Nếu người chấm cài APK khi backend không chạy:

| | |
|---|---|
| Xem địa điểm | ❌ bảng lỗi — cache rỗng nên `delegate.onError` |
| Tạo lịch trình | ⚠️ chạy được nhưng chỉ với **9 địa điểm mẫu** hard-code |
| Thời tiết, bản đồ | ✅ dùng Open-Meteo / OSRM công khai |
| Ảnh, đăng nhập, hồ sơ, cộng đồng, PDF QR | ❌ cần Django |

App không crash, nhưng màn hình người chấm mở đầu tiên lại là màn hỏng.

**Hướng xử lý:** xuất 242 địa điểm ra JSON đặt trong `assets/`, cho `PlaceCache` tự nạp khi
rỗng. Danh mục, tìm kiếm và lịch trình sẽ chạy bằng dữ liệu thật ngay từ lần mở đầu tiên.

---

## 1. 🔴 Bảo mật — phát sinh từ tính năng đăng nhập

### 1.1 Mật khẩu gửi qua HTTP không mã hoá

`LoginActivity` và `RegisterActivity` POST `username` + `password` tới
`http://<IP LAN>:8000/api/login/`. Manifest có `usesCleartextTraffic="true"`, nên toàn bộ
gói tin đi ở dạng chữ thô.

Bất kỳ ai trong cùng mạng Wi-Fi đều bắt được mật khẩu bằng công cụ phổ thông. Trong phòng
lab của trường thì đây là mạng dùng chung.

Hai việc cần làm:
- **Trước mắt:** ghi rõ trong báo cáo đây là giới hạn đã biết của bản dev; đừng dùng mật khẩu
  thật khi demo.
- **Đúng nhất:** backend chạy HTTPS, và thay `usesCleartextTraffic` bằng
  `network_security_config.xml` chỉ mở cleartext cho IP LAN, chỉ ở build debug.

### 1.2 Phiên đăng nhập mất mỗi lần tắt app

`JournifyApplication` đặt `CookieHandler.setDefault(new CookieManager())`. `CookieManager`
mặc định lưu cookie **trong bộ nhớ**, không xuống đĩa. Tiến trình bị huỷ là mất phiên,
người dùng phải đăng nhập lại từ đầu.

`ProfileFragment:226` đăng xuất bằng cách thay hẳn `CookieManager` mới — cách này chạy được
nhưng chỉ vì cookie nằm trong RAM.

Nếu muốn giữ phiên: cài `CookieStore` ghi xuống `SharedPreferences`. Nếu không kịp, ít nhất
phải **biết và giải thích được** khi vấn đáp — người chấm rất dễ thử tắt/mở lại app.

### 1.3 `allowBackup="true"`

Manifest dòng 12. Room database (chuyến đã lưu, catalog cache) được Android tự sao lưu lên
Google Drive của người dùng. Nên tắt, hoặc khai báo loại trừ trong `backup_rules.xml`.

---

## 2. 🔴 Rò rỉ tài nguyên — tệ hơn trước khi merge

### 2.1 16 executor được tạo, chỉ 2 chỗ gọi `shutdown()`

Mỗi repository tự tạo `Executors.newSingleThreadExecutor()` trong constructor. Không có
chỗ nào đóng chúng lại.

### 2.2 Mười chỗ tạo repository ngay trong listener

Mỗi lần bấm là sinh thêm một luồng sống mãi:

```
ItineraryActivity:315, :332   new RoomSavedTripRepository(this)
ItineraryActivity:407         new RemoteItineraryShareRepository(...)
ItineraryActivity:410         new RemoteWeatherRepository()
SavedTripsFragment:61, :82    new RoomSavedTripRepository(...)
HomeFragment:49               new CachingWeatherRepository(...)
PlaceDetailActivity:131       new RemotePlaceReportRepository(...)
ReplacementActivity:78        new CachingCatalogRepository(...)
```

Trước merge là 6 chỗ, giờ là 10. Tính năng mới lặp lại đúng khuôn cũ.

**Cách sửa:** một class `AppExecutors` dùng chung; repository nhận executor từ đó. Activity
giữ repository làm field, khởi tạo một lần trong `onCreate`.

---

## 3. 🟡 Tải ảnh — cuộn nhanh vẫn hụt

Nhánh merge đã thêm `ImageDiskCache` (25 MB, tự cắt bớt) — tốt. Nhưng ba vấn đề còn nguyên:

### 3.1 Hàng đợi FIFO 3 luồng làm ảnh đang hiển thị bị bỏ đói

`Executors.newFixedThreadPool(3)` dùng hàng đợi **không giới hạn, cũ nhất trước**. Cuộn qua
50 mục là nạp 50 tác vụ; pool chạy 3 cái một theo thứ tự nạp. Khi dừng cuộn, ảnh của các mục
**đang hiển thị** nằm cuối hàng đợi, phải chờ hàng chục ảnh đã trôi khỏi màn hình tải xong.

**Sửa:** dùng `ThreadPoolExecutor` với `LinkedBlockingDeque` và cho `offer()` gọi `offerFirst()`
— hàng đợi thành ngăn xếp, mục mới bind nhất được phục vụ trước.

### 3.2 Không kiểm tra tác vụ lỗi thời trước khi chạm mạng

Việc kiểm tra `url.equals(target.getTag())` chỉ nằm ở **cuối**, sau khi đã tải xong. Mục đã
trôi khỏi màn hình vẫn tải đủ 0.3–2.8 MB rồi mới bị vứt.

**Sửa:** kiểm tra tag ngay dòng đầu của tác vụ, trước khi mở kết nối.

### 3.3 Không giảm mẫu khi giải mã

`BitmapFactory.decodeByteArray(bytes, 0, bytes.length)` giải mã ở độ phân giải gốc.
`P007.png` là 2592×1944 → **19.2 MB RAM**, để hiển thị trong ô thumbnail 104dp (≈293 px trên
máy 450 dpi). Lãng phí khoảng 60 lần, và là rủi ro `OutOfMemoryError` khi cuộn.

Cache đĩa **không** giải quyết việc này — nó lưu byte nén, chi phí giải mã vẫn y nguyên.

**Sửa:** đọc header trước bằng `inJustDecodeBounds = true`, tính `inSampleSize` theo kích
thước `ImageView`, rồi giải mã lần hai.

---

## 4. ✅ Backend — đã sửa (xem mục 0)

*Đã sửa xong ngày 03/09 — giữ lại phần mô tả bên dưới vì nó giải thích bản chất vấn đề,
dùng được cho báo cáo.*

### 4.1 Ma trận khoảng cách 253.616 dòng chưa bao giờ được dùng

`get_location_matrix_id` sinh mã tra cứu từ **khoá chính Django**:

```python
return f"P{loc_id:03d}"     # Poi id=80 -> "P080"
```

Nhưng `dalat_distances.db` dùng mã từ CSV gốc: `P001`–`P079`, `E001`–`E163`. Khoá chính
Django là POI 80–158, Eatery 164–326. **Khớp 0/242.**

Hệ quả:
- File 19.3 MB dữ liệu đường bộ tính sẵn nằm không
- **Mọi lộ trình được tối ưu trên khoảng cách đường chim bay**, không phải đường đi thật —
  tức tính năng "Đường đi tối ưu" quảng cáo ở màn Home đang chạy sai dữ liệu
- Mỗi request mở 54 kết nối SQLite để tìm những dòng không thể tồn tại

Cột `image_code` là khoá nối đúng: khớp **242/242**.

### 4.2 Mở kết nối SQLite mới cho mỗi lần tra cứu

`get_distance_from_db` gọi `sqlite3.connect()` rồi `close()` mỗi lần. 54 lần một request,
tốn 38.7 ms trong tổng 168 ms (**23%**). Nên dùng một kết nối read-only chung mỗi luồng.

### 4.3 N+1 khi chuẩn hoá kết quả

Vòng lặp qua các điểm dừng gọi `Poi.objects.filter(id=...).first()` từng cái một — 22 truy
vấn một-dòng cho chuyến 3 ngày. Thay bằng `in_bulk` còn 2.

### 4.4 32 lệnh `print()` tiếng Việt

Chạy qua `manage.py runserver` thì không sao vì `manage.py` đã `reconfigure(encoding='utf-8')`.
Nhưng khi deploy qua `wsgi.py`/gunicorn, `print()` tiếng Việt ném `UnicodeEncodeError` và
**endpoint tạo lịch trình crash**. Có một lệnh nằm trong khối `try` khiến việc tra cứu Chợ Đà Lạt
âm thầm rơi vào nhánh fallback.

Chuyển sang `logging` là hết cả hai vấn đề, đồng thời tắt được log ồn khi chạy thật.

> Đo đạc trước đó: sửa 4.1–4.3 đưa endpoint từ **168 ms xuống 96 ms**, truy vấn 26→10,
> kết nối SQLite 54→6, và tuyến đường bắt đầu dùng khoảng cách đường bộ thật.

### 4.5 🟢 App trộn hai kiểu API

App gọi 11 endpoint, trong đó 4 cái là của **web cũ**, không phải `api/mobile/`:

```
api/login/   api/register/   api/logout/   api/shared-itineraries/
```

Chúng dựa vào session + CSRF của Django web. Chạy được, nhưng khiến ranh giới API mobile
không còn rõ ràng — nên nói rõ trong báo cáo, hoặc gom về `api/mobile/` cho nhất quán.

---

## 5. 🟡 Kiến trúc — chưa đổi

### 5.1 Vẫn không có `ViewModel` hay `LiveData` nào

`build.gradle.kts` vẫn khai báo `lifecycle-viewmodel` và `lifecycle-livedata`. Tìm khắp 65 file:
**con số 0**. Toàn bộ state nằm trong field của Activity/Fragment.

Đây là lỗ hổng lớn nhất khi bảo vệ phần kiến trúc. Hai ViewModel (`HomeViewModel`,
`ItineraryViewModel`) là đủ để nói "MVVM có thật".

### 5.2 `ItineraryActivity` là Activity duy nhất có `onSaveInstanceState`

Tám Activity còn lại chưa lưu gì. Yêu cầu của giảng viên ghi thẳng *"demonstrate lifecycle
handling"*, và người chấm gần như chắc chắn sẽ xoay máy.

### 5.3 Presentation import thẳng infrastructure

Chiều phụ thuộc đúng là `presentation → domain ← infrastructure`. Hiện tại Activity tự tay
dựng chuỗi `new CachingCatalogRepository(this, new RemoteCatalogRepository(...), null)`.

Một `di/ServiceLocator.java` khoảng 40 dòng là xử lý được, không cần Hilt. **Nhưng task này
đụng nhiều file trải khắp — để một người làm sau khi các nhánh khác đã merge.**

---

## 6. ✅ Chặn việc nộp bài — phần cấu hình đã xong

| Việc | Trạng thái |
|---|---|
| `signingConfig` | ✅ keystore + mật khẩu trong `local.properties` (đã gitignore) |
| Base URL cho release | ✅ đọc `journify.releaseBaseUrl` riêng, không còn kế thừa IP LAN |
| `applicationId` | ✅ `com.journify.app` |
| `.venv/` 421 MB, `static/` 129 MB, `build/` | ⬜ vẫn phải loại thủ công trước khi nén |
| Backend công khai cho APK release | ⬜ chưa quyết — ngrok, hoặc chấp nhận offline |

APK đã xác minh: `apksigner verify` → **Verifies** (v2), `CN=Journify`, 7.7 MB.

> **Cảnh báo:** mật khẩu keystore chỉ nằm trong `local.properties` trên một máy. Mất file đó
> là không bao giờ ký lại được bằng cùng khoá. Cần sao lưu ra ngoài project.

## 7. 🟢 Chất lượng & hoàn thiện

### 7.1 24 string thiếu bản tiếng Anh

247 string tiếng Việt, 223 tiếng Anh. Android fallback về locale mặc định nên app không vỡ,
nhưng ở chế độ English sẽ lòi ra tiếng Việt xen kẽ. Cần rà và bổ sung 24 string còn thiếu.

### 7.2 Chưa có chế độ tối

Không có `res/values-night/`. Rẻ, và rất nổi khi quay video demo.

### 7.3 Năm file còn dòng nén trên 200 ký tự

`MealSlotMapper`, `WeatherCodeMapper` (2 dòng), `WeatherSnapshot`, `RemoteWeatherRepository`,
`PlaceAdapter`. Đây là những chỗ khó bảo vệ nhất khi vấn đáp.

### 7.4 Hai file quá lớn

`ItineraryPdfExporter` **656 dòng** và `ItineraryActivity` **475 dòng**. Riêng
`ItineraryActivity` vẫn dựng danh sách điểm dừng bằng code Java thay vì `RecyclerView` —
chuyến 7 ngày × 6 điểm là 42 card cùng tồn tại, không tái sử dụng view.

### 7.5 Vài chuỗi hard-code trong Java

Ví dụ `LoginActivity` trả về `"Login failed. Please try again."` và `"Network error: ..."`
bằng tiếng Anh cứng, không qua `strings.xml` nên không dịch được.

---

## 8. Thứ tự đề xuất — còn 2 ngày

**Bắt buộc (không làm là mất điểm cứng hoặc không nộp được):**
1. `signingConfig` + base URL cho release (mục 6)
2. Đổi `applicationId`
3. Sửa `JAVA_HOME` trong README
4. Dọn `.venv/`, `build/` trước khi nén

**Đáng làm nhất theo tỉ lệ điểm / công sức:**
5. Áp lại bản sửa backend (4.1–4.4) — endpoint nhanh gần gấp đôi, và **sửa được lỗi tuyến
   đường đang tính sai**; đây là lỗi ảnh hưởng tới tính đúng đắn của tính năng cốt lõi
6. Dọn rò rỉ executor (mục 2) — lỗi thật, sửa cơ học
7. Ba sửa đổi cho `RemoteImageLoader` (mục 3) — gọn trong một file
8. Bổ sung 24 string tiếng Anh (7.1)
9. Hai `ViewModel` (5.1) — điểm kiến trúc cao nhất
10. Dark mode (7.2)

**Ghi vào báo cáo thay vì sửa (không kịp thì phải nêu ra):**
- Mật khẩu đi qua HTTP trong bản dev (1.1)
- Phiên đăng nhập không tồn tại qua lần khởi động lại (1.2)
- API mobile không có xác thực/giới hạn tần suất ở các endpoint `api/mobile/`
- Phần backend kế thừa từ web (35 route, app dùng 11) so với phần viết mới cho mobile

---

## 9. Điểm đang làm tốt

- **Chống race condition khi tìm kiếm** bằng biến đếm phiên bản request — lỗi phổ biến mà
  project này xử lý đúng
- **Debounce 280 ms** khi gõ
- **`mobile_profile` kiểm tra `is_authenticated`** và trả 401 đúng chuẩn
- **Có rate limit 5 lần/phút theo IP** ở view đăng nhập
- **Cache ảnh xuống đĩa** giới hạn 25 MB, tự cắt bớt khi vượt
- **Chế độ offline** có `OfflineItineraryBuilder` + catalog cache, kèm 8 unit test
- **Song ngữ đúng chuẩn** `AppCompatDelegate.setApplicationLocales`
- **Chia sẻ file qua `FileProvider`**, không xin quyền lưu trữ rộng
- **Domain thuần Java**, không import `android.*`
- **25 unit test** — tăng từ 2 lúc đầu dự án
