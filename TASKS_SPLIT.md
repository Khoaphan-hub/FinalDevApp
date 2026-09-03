# Phân chia công việc — 2 người, không đụng file nhau

**Ngày lập:** 01/09/2026 · **Deadline:** 23:59 ngày 05/09/2026
**Nguyên tắc:** mỗi file chỉ **một người** được sửa. Không ai đụng file của người kia, kể cả sửa một dòng.

Phần đăng nhập/đăng ký (`LoginActivity`, `RegisterActivity`, tab Hồ sơ) do **người thứ ba** làm, không nằm trong tài liệu này.

---

## BƯỚC 0 — Làm trước khi chia (khoảng 30 phút, một người làm)

Ba việc này gỡ bỏ toàn bộ khả năng đụng nhau. **Phải xong và commit trước khi hai người bắt đầu.**

### 0.1 Tạo `AppExecutors` dùng chung

Cả hai người đều cần nó cho task A5. Nếu ai cũng tự tạo thì sẽ ra hai class trùng chức năng.

Tạo `infrastructure/concurrent/AppExecutors.java`: một `ExecutorService` tĩnh dùng chung toàn app + một `Handler` main thread. Sau đó các repository nhận executor từ đây thay vì tự `Executors.newSingleThreadExecutor()` trong constructor.

### 0.2 Format lại `strings.xml`

Hiện tại hai file `values/strings.xml` và `values-en/strings.xml` nhồi 10–15 `<string>` trên **cùng một dòng**. Cả hai người đều sẽ thêm string mới → git merge sẽ xung đột cả khối, rất khó gỡ.

Format lại mỗi string một dòng (Android Studio: mở file, `Ctrl+Alt+L`). Sau đó hai người thêm string ở hai chỗ khác nhau sẽ merge sạch.

### 0.3 Chốt quy ước

- **Không đổi chữ ký public** của class mà người kia đang gọi. Ví dụ Người A sở hữu `RoomSavedTripRepository`, nhưng Người B gọi nó từ `ItineraryActivity` — A được đổi ruột thoải mái, **không được đổi** tên method hay tham số constructor. Nếu buộc phải đổi thì báo trước.
- Mỗi người làm trên nhánh riêng, merge vào `Khai_2` từng task một, không dồn cuối.
- Chạy `gradlew assembleDebug testDebugUnitTest` trước mỗi lần commit.

---

## NGƯỜI A — Home, Trips, tầng dữ liệu

### File thuộc sở hữu (chỉ A được sửa)

```
presentation/MainActivity.java
presentation/home/HomeFragment.java
presentation/home/HomeViewModel.java              (tạo mới)
presentation/saved/SavedTripsFragment.java
infrastructure/remote/RemoteWeatherRepository.java
infrastructure/local/repository/RoomSavedTripRepository.java
domain/repository/WeatherRepository.java          (tạo mới)
res/values-night/                                 (tạo mới)
```

### Task

**A-1. Sửa mất trạng thái khi xoay ở `MainActivity`** — *ưu tiên cao*

`showDestination()` gọi `new HomeFragment()` mỗi lần được gọi. Khi xoay máy, FragmentManager đã tự khôi phục fragment cũ, nhưng `BottomNavigationView` cũng khôi phục tab đang chọn → listener bắn → `replace()` bằng fragment mới tinh. Hậu quả: gọi lại API thời tiết mỗi lần xoay, mất vị trí cuộn.

Sửa: chỉ tạo fragment mới khi tab **thực sự đổi**. Kiểm tra fragment hiện tại trong `fragment_container` trước khi `replace`, hoặc dùng tag để tìm lại instance cũ.

**A-2. Dọn rò rỉ thread** — *ưu tiên cao*

- `HomeFragment.java:49` — `new RemoteWeatherRepository()` nằm trong hàm load, mỗi lần bấm "Thử lại" sinh một `ExecutorService` mới.
- `SavedTripsFragment.java:61` và `:82` — `new RoomSavedTripRepository(...)` trong hai method, mỗi lần xoá/tải lại sinh thêm một cái.

Sửa: tạo repository **một lần** ở `onCreate`/`onViewCreated` và giữ làm field, đồng thời cho chúng dùng `AppExecutors` từ bước 0.1.

**A-3. Thêm `HomeViewModel`** — *ưu tiên trung bình*

Hiện tại **cả app không có ViewModel nào**, dù `lifecycle-viewmodel` và `lifecycle-livedata` vẫn nằm trong `build.gradle.kts`. Đây cũng là gốc rễ của A-1.

Tạo `HomeViewModel` giữ `LiveData<WeatherSnapshot>`, `HomeFragment` quan sát nó. Thời tiết sẽ không tải lại khi xoay máy nữa. Đây là điểm cộng lớn cho phần kiến trúc trong báo cáo.

**A-4. Thêm interface `WeatherRepository`** — *ưu tiên trung bình*

Ba repository kia (planner, catalog, saved-trip) đều có interface ở tầng domain, riêng thời tiết thì không, nên `HomeFragment` phải import thẳng class infrastructure. Thêm interface cho nhất quán.

**A-5. Format lại `RemoteWeatherRepository`** — *nhanh, 15 phút*

File này còn một dòng dài **1162 ký tự** gói cả HTTP lẫn parse JSON. Tách thành các method có tên rõ ràng. Đây là chỗ khó bảo vệ nhất khi vấn đáp.

**A-6. Bỏ Java Serialization khi lưu chuyến đi** — *ưu tiên trung bình*

`RoomSavedTripRepository` ghi `ObjectOutputStream` của cả `Itinerary` vào cột BLOB. Chỉ cần thêm/đổi một field trong `ItineraryStop` là **toàn bộ chuyến đã lưu của người dùng biến mất, im lặng** (code hiện đang `catch` rồi bỏ qua).

Sửa: lưu JSON bằng `org.json` (đã có sẵn, không thêm thư viện). JSON bỏ qua field lạ thay vì vỡ. **Giữ nguyên chữ ký public** vì Người B đang gọi class này.

**A-7. Dark mode** — *nếu còn thời gian*

Tạo `res/values-night/themes.xml` đảo màu nền/chữ. Rẻ và rất nổi trong video demo.

---

## NGƯỜI B — Lịch trình, Catalog, Ảnh

### File thuộc sở hữu (chỉ B được sửa)

```
presentation/itinerary/ItineraryActivity.java
presentation/itinerary/ItineraryViewModel.java          (tạo mới)
presentation/itinerary/ItineraryStopAdapter.java        (tạo mới)
presentation/catalog/CatalogActivity.java
presentation/catalog/PlaceAdapter.java
presentation/selection/ReplacementActivity.java
infrastructure/remote/RemoteImageLoader.java
res/layout/item_itinerary_stop.xml                      (tạo mới)
res/layout/activity_itinerary.xml
```

### Task

**B-1. Giảm mẫu ảnh khi giải mã** — *ưu tiên cao*

Số liệu thật của project: 242 ảnh, tổng ~79 MB. File nặng nhất `P007.png` là **2592×1944**, giải mã thành **19.2 MB RAM**, để hiển thị trong ô thumbnail `104dp` (≈293 pixel trên máy 450dpi). Lãng phí khoảng 60 lần. Cuộn vài chục mục là đủ `OutOfMemoryError`.

Sửa trong `RemoteImageLoader`:
1. Đọc dữ liệu tải về vào `byte[]` (`InputStream` từ `HttpURLConnection` không tua lại được).
2. Giải mã lần 1 với `inJustDecodeBounds = true` — chỉ đọc header lấy kích thước, không cấp phát pixel.
3. Tính `inSampleSize` là luỹ thừa của 2 sao cho ảnh ra vẫn ≥ kích thước ô hiển thị.
4. Giải mã lần 2 từ `byte[]` với `inSampleSize` đó.

`P007` sẽ từ 19.2 MB xuống còn khoảng 0.3 MB.

**B-2. Dọn rò rỉ thread trong `ItineraryActivity`** — *ưu tiên cao*

- Dòng 114 — `new RoomSavedTripRepository(this)` nằm trong `onClick` nút Lưu.
- Dòng 316 — `new RemoteItineraryShareRepository(...)` nằm trong hàm xuất PDF.
- `pdfExecutor` không bao giờ được `shutdown()`.

Sửa: tạo field một lần trong `onCreate`, dùng `AppExecutors` từ bước 0.1, và `shutdown()` trong `onDestroy`.

**B-3. Chuyển danh sách điểm dừng sang `RecyclerView`** — *ưu tiên trung bình*

`ItineraryActivity.renderDay()` dựng `MaterialCardView`/`LinearLayout`/`TextView` bằng code Java rồi `addView` hết vào một `LinearLayout` — khoảng 85 dòng, không tái sử dụng view. Chuyến 7 ngày × 6 điểm = 42 card cùng tồn tại, mỗi card kèm một ImageView tải mạng.

Sửa: `RecyclerView` + file `item_itinerary_stop.xml`. Đã có sẵn `PlaceAdapter` làm mẫu để copy cấu trúc. Sẽ giảm `ItineraryActivity` còn khoảng một nửa số dòng.

**B-4. Thêm `ItineraryViewModel`** — *ưu tiên trung bình*

`onSaveInstanceState` đã được thêm rồi nên chuyến đi không còn mất khi xoay, nhưng trạng thái vẫn nằm trong field của Activity. Đưa `Itinerary` đang chỉnh sửa vào ViewModel sẽ sạch hơn và đồng bộ với việc Người A làm `HomeViewModel`.

**B-5. Fallback offline cho catalog** — *ưu tiên trung bình*

`PlaceSelectionActivity` có `ResilientPlannerRepository` (remote → demo), nhưng `CatalogActivity` và `ReplacementActivity` chỉ có `RemoteCatalogRepository`. Tắt Django là hai màn này chỉ hiện lỗi.

Sửa: cache danh mục lần tải gần nhất (Room hoặc `SharedPreferences` dạng JSON), hiện bản cache kèm nhãn "dữ liệu offline". Yêu cầu của giảng viên ghi rõ *"explicit loading, empty, error, and offline states"*.

> Lưu ý: `PlaceSelectionActivity.java` **không thuộc sở hữu của ai** trong hai lane. Nếu B cần sửa nó cho B-5 thì báo trước — A không đụng file này nên thực tế B cứ lấy, chỉ cần nói ra.

---

## KHÔNG CHIA — làm sau khi cả hai merge xong

**B2 — `ServiceLocator`.** Task này sửa import ở **17 chỗ** trải khắp `presentation/`, tức là đụng file của cả hai người. Nếu làm song song sẽ xung đột chắc chắn.

Để **một người làm sau cùng**, khi cả hai lane đã merge xong. Nội dung: tạo `di/ServiceLocator.java` trả về kiểu interface của domain, rồi đổi các Activity để không import thẳng `infrastructure` nữa. Chỉ đổi import + một dòng khởi tạo mỗi màn hình.

---

## TRƯỚC KHI NỘP — một người làm, sát ngày

Những việc này đụng file cấu hình chung, đừng làm song song.

| Việc | File | Ghi chú |
|---|---|---|
| Tạo keystore + `signingConfig` | `app/build.gradle.kts` | **Bắt buộc** — rubric yêu cầu `apk/app-release.apk` |
| Đổi `applicationId` khỏi `com.example.finalproject` | `app/build.gradle.kts` | `com.example.*` là namespace mẫu |
| Thay `usesCleartextTraffic` bằng `network_security_config.xml` | `AndroidManifest.xml` | Chỉ cho phép HTTP với IP LAN, chỉ ở build debug |
| Đặt base URL release thành HTTPS đã deploy | `app/build.gradle.kts` | Hiện đang là placeholder `journify.example.com` |
| Sửa `JAVA_HOME` trong tài liệu | `README.md`, `JOURNIFY_MIGRATION.md` | Đang ghi `D:\AndroiStudio\jbr`; máy hiện tại là `C:\Program Files\Android\Android Studio\jbr` → thành viên khác build sẽ lỗi |
| Đặt `DJANGO_DEBUG=False` khi deploy | biến môi trường | `settings.py` mặc định vẫn là `True` |

---

## Kiểm tra nhanh trước mỗi lần merge

```bash
gradlew assembleDebug testDebugUnitTest
```

Hiện có 4 file test (`GenerateItineraryUseCaseTest`, `ItineraryEditorTest`, `TripAreaTest`, `WeatherCodeMapperTest`). Cả hai lane nên thêm test cho phần mình làm — đây là code thuần Java nên test rất rẻ, và con số test trong báo cáo nhìn tốt hơn nhiều.

## Bật backend để test

```bash
cd backend && .venv/Scripts/python.exe manage.py runserver 0.0.0.0:8000 --noreload
```

Database đã có sẵn 79 điểm tham quan và 163 quán ăn. IP máy dev nằm ở `local.properties` (khoá `journify.devServerIp`), mỗi người tự đặt theo máy mình — file này đã được gitignore nên không đụng nhau.
