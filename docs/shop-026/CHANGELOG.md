# Fantasy Shop 0.2.6

- Preserve the Fantasy HUD dark/gold design with three adjacent CLASS / SHOP / BUNDLE tabs, active states and independent page navigation.
- Replace flattened server item names with registry-backed ItemStack components, localized on the client. Bundle rows include native icons, quantities and item tooltips; clicking a row selects its larger preview.
- Measure text with Minecraft's renderer. Ellipsize product names with full hover text, wrap descriptions and scroll details while keeping confirmation/payment controls visible.
- Localize shop labels, catalog descriptions, class names and purchase feedback in English and Vietnamese. Preserve registered item IDs and existing item translations.
- Display current/owned/cooldown/locked states. Owned class switching remains free after cooldown; early switching offers BeastCoin only.
- Reject invalid configured item references; visibly disable invalid client previews and log registry IDs and expected missing resources.
- Suppress Minecraft toasts while the shop is open; queued toasts remain available afterward.
- Expand the bounded compressed HUD catalog allowance to accommodate full bundle components. Keep the 256 KiB compressed packet limit and reject oversized/malformed input.

No WorldBoss, Infinity, Cobblemon AI, balance storage, reward distribution or transaction journal implementation changes.
