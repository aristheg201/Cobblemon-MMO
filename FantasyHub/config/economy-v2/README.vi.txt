GIÁ MUA RPG — ECONOMY V2
Class thường: mặc định 3000 beastcoin, được cấu hình 1000..5000 (số nguyên).
Dragonborn / Death Knight: chính xác 2 huntercoin mỗi lần chọn/đổi class có tính phí.
Giữ nguyên điều kiện mở khóa, quyền và cooldown; không cấp quyền class chỉ vì thanh toán.
Không thay đổi economy.currency chung, không quy đổi 25000 thành huntercoin trong giao dịch này.
Ánh xạ đã xác nhận: BEconomy:secondary = beastcoin; BEconomy:primary = huntercoin. Primary chỉ dùng cho hai class đặc biệt giá 2, không dùng cho class thường hoặc skill beastcoin.

Skill Cobblemon: người chơi tự chọn beastcoin hoặc CobbleDollars trong cả GUI client và menu inventory server.
Giá mặc định: 100 beastcoin HOẶC 500000 CobbleDollars cho mỗi skill. Hai bảng giá độc lập.
Giá riêng được cấu hình nhưng phải >=100 beastcoin / >=500000 CobbleDollars, số nguyên.
Gửi client chỉ ID phương thức; server tự đọc giá và tiền tệ, kiểm tra sở hữu, trừ tiền, lưu skill và hoàn tiền nếu lưu thất bại.
Skill vẫn lưu chung trong SVFrameMMO, xuất hiện trong /skills và HUD.

JAR mới tự nâng cấu hình class cũ (có backup) và giá skill cũ lên v2.
Các override CobbleDollars cũ <500000 được nâng lên 500000; override cao hơn được giữ.
Không thay đổi số dư. Sau migration, cấu hình giá dưới ngưỡng bị từ chối.
Script sv_class_switch.sk nguyên gốc được nhận diện bằng SHA256 và backup trước khi chuyển command trả phí về native transaction.
Script đã chỉnh riêng không bị ghi đè: hãy kiểm tra rồi dùng script trong gói, hoặc chuyển phần thanh toán sang:
    execute console command "fantasyhub classpurchase %player% %{_class}%"
Không để script vừa remove balance vừa gọi classpurchase (sẽ thu tiền hai lần).
class-pricing.yml là các khóa để ghép vào config/FantasyHub/class-switch.yml, không thay cả file vì còn quyền/cooldown.
pokemon-skill-pricing.json là nội dung để ghép vào pokemon-skills của config/SVFrameMMOCobblemon/config.json, không thay cả config fusion/VFX.
