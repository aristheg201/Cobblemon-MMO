# Cosmic expansion — WorldBoss 0.1.1 / class 0.2.4-r2

Bản giao trước QA gameplay theo yêu cầu. Build và test tự động được chạy; chưa xác nhận animation, cân bằng hoặc gameplay mới trong Minecraft.

Cài trên bộ class 0.2.4 đã giao. Server: thay Bridge 0.2.4, Integration 0.1.19 và WorldBoss cũ bằng đúng JAR trong gói; chỉ giữ một bản mỗi mod ID. Client: thay FantasyHubClient 0.2.4 trong gói. Hai bên dùng asset revision `heroes-infinity-v6`; không dùng client cũ. Giữ các dependency của bộ class/Cobblemon 1.8.2. ShadowCrates-2.3.jar do người dùng cung cấp được giữ nguyên trong server/mods để gọi animation CSGO của chính mod đó; cần Fabric Language Kotlin. Không cài WorldBoss/ShadowCrates lên client multiplayer.

Merge config, không chép đè arena tùy chỉnh. `cosmic-bosses-merge.json.example` chứa 5 boss mới để thêm vào mảng bosses đang có. `bosses.json` là bộ mặc định đầy đủ. `infinity.json` tự sinh nếu thiếu; không xóa `infinity-state.json` để reset cooldown.

## Boss và loot

- `thanos`: 10.000 HP thật; giảm 85% sát thương; bossbar tím với tên §5§lThanos, The Mad Titan. Skin từ URL người dùng gửi. Titan Punch hút trong 6 block rồi đấm, Slowness IV/Blindness; Titan Beam theo mục tiêu có tốc độ xoay giới hạn, gây damage liên tục 3 giây; Cosmic Rain có vòng báo rơi và 1,5 giây delay, i-frame khi ẩn trong portal; dưới 50% HP tự kích hoạt Snap, bay và bất tử khi charge 5 giây. Tia từ boss kiểm tra cover solid; đứng sau cột đủ cao để sống sót. Sau đòn cuối có wave Pokémon hệ Dark. Mod không phá/đặt lại địa hình: chuẩn bị sẵn cột trong arena.
- `collector`: phase 100–85% stasis theo threat; 85–60% hút buff thành absorption; 60–30% gọi các Pokémon Dark hung hãn; dưới 30% gravity lift/pull và burst. Có mind control đổi team tạm thời và ép đánh gần, tuân theo PvP/adapter của server; team gốc được lưu và phục hồi. Ally đánh người bị stasis để giải thoát.
- `speedster`: class Flash, Space Stone.
- `sorcerer_guardian`: Sorcerer Supreme, Mind/Time Stone.
- `ultimate_trial`: luân phiên sáu class superhero native, Soul Stone.
- Thor: Power Stone; Collector: Reality Stone. Tất cả boss mặc định có 15% xác suất rơi một stone, một lượt roll cho mỗi boss thắng, không phải mỗi người. Thanos roll riêng 20% gauntlet. `stoneDrops` chọn tập stone, `stoneDropChance`/`gauntletDropChance` và mitigation chỉnh được trong config. Không phát loot khi abort/thua. Equipment hiển thị của boss không tạo thêm gauntlet ngoài lượt roll.

Ví dụ: `/worldboss setarena main 32`, `/worldboss test start thanos main`, `/worldboss start collector main`, `/worldboss abort`. Đặt lịch bằng cơ chế schedule đã có. HP tối đa cấu hình được nâng lên 1.000.000, hai bên cùng nâng giới hạn native attribute.

## Stones và gauntlet

Item ID: `fantasyhub:space_stone`, `power_stone`, `reality_stone`, `mind_stone`, `soul_stone`, `time_stone`, `infinity_gauntlet`. Có trong creative Combat. OP dùng `/infinity give <space|power|reality|mind|soul|time|gauntlet>`.

- Space: right-click blink sau player dưới crosshair trong 15 block hoặc portal tới phía trước; kiểm tra block/collision.
- Power: cầm/equip được Strength II + Resistance II; active blast 360° trong 6 block.
- Reality: cầm/equip tăng trưởng sapling, Fortune III tối thiểu khi đào ore (giữ enchant gốc của tool), tăng trọng số rare spawn trong chunk đang đứng; active hồi máu/đói trong 5 block.
- Mind: khóa mục tiêu 3 giây.
- Soul: cầm/equip khiến mob và hostile Pokémon không chọn/đánh mình; active miễn sát thương 5 giây.
- Time: có trong inventory/equipment sẽ đảo ngược chết tại chỗ, còn 50% HP/đói; active khóa mục tiêu quanh 10 block trong 5 giây và buff Speed/Regeneration/Saturation. Freeze chặn di chuyển, attack/use và cast class, đồng thời tạm dừng AI boss.
- Stones dùng được trực tiếp bằng right-click; có thể equip Back qua Accessories/Trinkets. Cầm gauntlet và giữ stones trong inventory cũng kích hoạt passive tương ứng.
- Gauntlet: sprint + right-click để đổi stone, right-click để dùng stone đang chọn. Sneak + right-click SNAP, cần đủ sáu stones. Item không mất độ bền.

## SNAP

Cooldown 30 phút lưu theo UUID trước khi animation bắt đầu; reconnect/đổi item không reset. ShadowCrates có mặt thì gọi API `startCSGOAnimation`; thiếu hoặc lỗi khởi chạy thì dùng roulette native. Callback/watchdog chống resolve lặp; trạng thái APPLYING sau crash không tự replay các hiệu ứng phá hủy.

`infinity.json.snapWeights` có đúng 10 trọng số không âm, mặc định đều là 1:

1. Tiền lớn. Mặc định native CobbleDollars; muốn EcoPower/Vault hoặc provider khác, đặt `moneyCommand` là lệnh console của bridge đang cài, dùng `{player}` và `{amount}`. Không nhúng Bukkit/Vault vào Fabric.
2. Tăng trọng số spawn một hệ Pokémon trên server 10 phút, có hạn dùng lưu lại qua restart.
3. Spawn một Legendary ngẫu nhiên trước mặt, danh sách species cấu hình được.
4. Giết một player online ngẫu nhiên và broadcast “X was erased from existence.”
5. Xóa 50% record Pokédex của người dùng hoặc player khác; giữ quyền sở hữu Pokémon.
6. Toàn bộ party + PC của người dùng ngất.
7. Toàn bộ party + PC của player khác ngất.
8. 50% wild Pokémon đang loaded thành bụi, không drop; loại owned/busy/boss/minion.
9. Giết người dùng.
10. Blank, vẫn mất cooldown.

**Party/PC chỉ đặt HP = 0, không remove/xóa Pokémon, slot, UUID hay item đang giữ.** Trước các thay đổi party/PC/Pokédex có JSON snapshot trong `config/svframeworldboss/snap-backups/`. Pokédex được sync lại sau khi thay đổi. `/infinity status` xem cooldown/ticket. Lỗi apply ghi FAILED, không tự roll lại; đối chiếu log và `infinity-state.json` trước khi xử lý thủ công.

## Nguồn và QA

Skin: https://www.minecraftskins.com/skin/20876321/thanos-minecraft-skin/
Gauntlet reference: https://www.curseforge.com/minecraft/mc-mods/endgame-infinity-gauntlet — dùng model/item native trong gói, không nhúng JAR Forge/NeoForge của mod reference.
ShadowCrates: JAR người dùng đính kèm; gọi API animation, không sao chép/decompile thành source của addon.

Các lượt QA 0.2.4/WorldBoss 0.1.0 trước đây không phải bằng chứng runtime cho bản Cosmic này. Cần QA sau: skin/held gauntlet, cả bốn đòn Thanos và cover, bốn phase Collector, khả năng giải stasis, restore team, AI/freeze/peace với modpack, cả 10 SNAP, animation ShadowCrates và persistence party/PC/Pokédex trên bản sao dữ liệu.
