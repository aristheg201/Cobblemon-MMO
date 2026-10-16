BẢN SỬA ĐỂ TEST — Fantasy Hub 0.1.9 / Minecraft 1.21.1 / Java 21

CÀI SERVER — BẢN BRIDGE THEO YÊU CẦU:
Dùng TẤT CẢ 6 JAR trong server/separate/. Thay phiên bản cũ của từng mod. Gỡ FantasyHubCore nếu có; không cài Core và Bridge cùng nhau.
Giữ SVFrameSkript-0.1.0.jar riêng nếu đang sử dụng script; bản này chưa thay đổi, chưa phải Skript đầy đủ 1:1.

CÀI CLIENT: thay FantasyHubClient cũ bằng client/FantasyHubClient-0.1.9.jar. Không đặt JAR client lên server dedicated. Server và client phải cùng bản 0.1.9.

HAI MOD ĐƯỢC VÁ: thay CobblePass cũ bằng compat/CobblePass-2.1.0-server-gui-6.jar; thay Guild Progression cũ bằng compat/guild-progression-1.1.0-mining-fix-1.jar. Không giữ 2 bản cùng mod. CobblePass cần GooeyLibs Fabric >=3.1.0 (đã kèm dependencies/GooeyLibs-Fabric-3.1.1-1.21.x.jar; cài server nếu chưa có, không cài trùng); Guild cần Cobblemon >=1.8.1. Giữ Fabric API, Kotlin, các dependency vốn có; giữ Tough As Nails và Filament hiện tại.
Sao lưu world và config trước khi thay. Dừng server, thay JAR, rồi khởi động.

CONFIG:
- Giữ config hiện có, không chép đè cả thư mục. Core tự cập nhật mặc định không sửa cấu hình tùy biến.
- config/SVFrameItems-compatibility.yml là mẫu cho config/SVFrameItems/compatibility.yml (auto Thermal Tuning và cho phép enchant đồ nhập).
- config/kabuto.yml là mẫu config/FantasyHub/kabuto.yml; hai tùy chọn zecter-regeneration và zecter-night-vision mặc định true.
- config/SVFrameMMO/config.yml, custom-mining.regeneration-enabled: false để tắt tái sinh quặng. Mặc định mới tắt; muốn bật cần chủ động đặt true. Không xóa toàn bộ cấu hình mining cũ.
- config/guild-contracts-fixed.json là cấu hình 51 hợp đồng đã sửa tag ore và beastcoin=>secondary; chỉ thay file hợp đồng tương ứng của Guild nếu chưa có chỉnh sửa riêng. minecraft:redstone_ore là BLOCK đúng; minecraft:redstone_ores là TAG đúng, không dùng thay thế lẫn nhau.
- BEconomy secondary=beastcoin, primary=huntercoin; không thay tỷ giá.

THAY ĐỔI TRONG BẢN NÀY:
- Tắt tái sinh ore mặc định; custom mining phát đúng sự kiện để Guild ghi nhận; sửa tag ore cũ.
- Henshin ẩn cosmetic Filament, giữ nguyên đồ đã trang bị, phục hồi khi về người.
- Thermal Tuning thật của TAN cho giáp/vũ khí SVFrameItems và Zecter; enchant table/anvil hoạt động. Auto Thermal không tạo glint tím và không tạo XP vô hạn từ grindstone.
- Rider/Hyper dùng lại texture Kabuto gốc; sửa tư thế Kunai lưỡi lên, không đổi tư thế Gun.
- Âm bắn Gun và chém Kunai mới; Zecter tự hồi máu và Night Vision, không xóa potion sẵn có khi tháo.
- CobblePass lưu an toàn, tạo lại thư mục thiếu, giữ trạng thái dirty để retry khi lỗi IO; giao thưởng yêu cầu lưu thành công.
- Reload MMO bỏ việc tính tài nguyên của hồ sơ offline chưa gắn dữ liệu player.

ĐÂY LÀ BẢN TEST CÁC SỬA LỖI TRÊN, KHÔNG PHẢI TUYÊN BỐ TOÀN BỘ PORT ĐÃ HOÀN THÀNH. Xem QA-STATUS.json cho bằng chứng và phần chưa kiểm chứng.
