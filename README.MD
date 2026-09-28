# AutoSellVDM

> **Tuyên bố bản quyền & Nguồn gốc:**
> - **Tác giả (Creator):** `LovOrLop` (`vuducmanh` - `vdm`)
> - **Nguồn:** Sản phẩm được leak từ **ZirVN** ([github.com/ZirVN/AutoSell](https://github.com/ZirVN/AutoSell))

---

## Các tính năng nổi bật

### 1. Tự động bán đồ (Auto Sell)
- **Chu kỳ bán thông minh:** Hỗ trợ nhịp bán linh hoạt (bán khi đầy kho hoặc bán theo lượt), tự động gõ lệnh bán (`/sell` hoặc lệnh tùy biến).
- **Tự động click xác nhận:** Tự động chuyển vật phẩm vào khu vực bán và click vào ô xác nhận (mặc định slot 53, có thể tùy chỉnh).
- **Tùy chỉnh tốc độ (Human Delay):** Giãn cách thời gian click chuột (ticks) tạo cảm giác tự nhiên như thao tác người thật, hạn chế bị server anticheat phát hiện hoặc kick.
- **Bộ lọc vật phẩm (Whitelist / Blacklist):** Chỉ bán các item được chỉ định hoặc bán tất cả trừ danh sách ngoại lệ.
- **Thống kê doanh thu:** Tự động phân tích số tiền nhận được từ tin nhắn chat server hoặc theo dõi biến động số dư tài khoản.

### 2. Báo cáo qua Discord Webhook
- Tự động gửi báo cáo doanh thu, số lần bán, tốc độ kiếm tiền (tiền/giờ) lên kênh Discord theo các mốc thời gian tùy chỉnh (1, 15, 30, 60 phút).
- Hỗ trợ gắn thẻ (Ping) User ID, tùy chỉnh tên hiển thị và Avatar của Webhook bot.

### 3. Tự động chuyển tiền (Auto Pay)
- Hỗ trợ tự động chuyển tiền sang tài khoản chính hoặc tài khoản phụ định kỳ khi đạt số dư nhất định.

### 4. Tự động kết nối lại (Auto Reconnect)
- Tự động reconnect vào server khi bị ngắt kết nối (mất mạng, server bảo trì, bị kick tạm thời...).
- Tự động khôi phục lại chu kỳ bán đồ sau khi kết nối thành công.

### 5. Bảo vệ an toàn (Staff Guard & Player Alert)
- **Staff Guard:** Nhận diện và phát hiện khi có Staff xuất hiện trong khu vực hoặc trong tablist, tự động dừng hành vi bất thường.
- **Player Alert:** Cảnh báo khi có người chơi lạ đến gần, gửi cảnh báo tức thì về Discord Webhook.

### 6. Giao diện & HUD In-game
- **HUD hiển thị:** Bảng số liệu thống kê thời gian thực trên màn hình game (doanh thu, thời gian chạy, trạng thái bot).
- **Giao diện GUI:** Tùy biến mọi thông số ngay trong game mà không cần chỉnh sửa thủ công file cấu hình.

---

## Cách sử dụng & Phím tắt

- **Mở Menu cài đặt:** Gõ lệnh `/autosellvdm` trong game hoặc gán phím tắt trong phần **Options -> Controls**.
- **Phím tắt nhanh (Hotkey):** Có thể gán phím nóng trong menu để bật/tắt nhanh Auto Sell, bật/tắt bảng thống kê hoặc chế độ cảnh báo.
- **File cấu hình:** Được lưu tự động tại `config/autosellvdm.json`.

---

## Yêu cầu & Cài đặt

1. Đặt file mod `.jar` vào thư mục `.minecraft/mods`.
2. Yêu cầu **Fabric Loader** (>= 0.19.0) và **Fabric API** phù hợp với phiên bản Minecraft.
3. Khởi động game và sử dụng lệnh `/autosellvdm` để thiết lập trước khi dùng.

---

## Miễn trừ trách nhiệm
- Mod được chia sẻ với mục đích học tập, nghiên cứu và trải nghiệm cá nhân.
- Vui lòng tuân thủ quy định và luật chơi của từng máy chủ.
