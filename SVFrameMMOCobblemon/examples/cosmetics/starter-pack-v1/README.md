# SVFrameMMOCobblemon Cosmetic Starter Pack v1

Pack thử nghiệm data-driven cho Integration 0.1.13+.

- Không được bundle/tự cài bởi JAR.
- Copy các file `.yml` trong thư mục này vào `config/SVFrameMMOCobblemon/cosmetics/`.
- Chạy `/cosmetics reload`.
- Admin test nhanh: `/cosmetics grantall <player>`.
- Player mở `/cosmetics`, hoặc dùng `/cosmetics preview <id>` và `/cosmetics equip <id>`.

## Slot

- AURA: hiệu ứng ngắn quanh thân. Không dùng emitter lifetime vô hạn.
- HEAD: halo/eye/crown bám anchor đầu.
- BACK: layer trái/phải phía sau lưng.
- ORBIT: điểm VFX quay quanh player.
- TRAIL: cố ý để dấu phía sau khi di chuyển.
- FOOTSTEP: chỉ phát khi di chuyển trên mặt đất.

## Hiệu năng

Pack dùng radius 20–24 block, viewer cap 24–32 và ưu tiên particle lifetime ngắn.
Không tăng các cadence AURA/HEAD/BACK lên 1–2 tick nếu server đông.
TRAIL/FOOTSTEP có movement-threshold để không emit khi đứng yên.

## Ghi chú

Các effect `cobblemon:*` trong pack là asset native của Cobblemon 1.7.3, không cần custom texture.
`HEAD/BACK/AURA` vẫn là Snowstorm spawn tại world-position ngắn hạn; particle được chọn ngắn để hạn chế ghost trail khi player chạy nhanh.
