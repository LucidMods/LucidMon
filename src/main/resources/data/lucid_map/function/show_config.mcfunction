function lucid_map:apply_config
tellraw @s [{"text":"[LucidMap] Coverage: ","color":"aqua"},{"score":{"name":"#area_x","objective":"lucid_map"}},{"text":" x "},{"score":{"name":"#area_z","objective":"lucid_map"}},{"text":" blocks"}]
tellraw @s [{"text":"[LucidMap] Interval: ","color":"aqua"},{"score":{"name":"#interval_ms","objective":"lucid_map"}},{"text":" ms = "},{"score":{"name":"#interval_ticks","objective":"lucid_map"}},{"text":" ticks"}]
tellraw @s [{"text":"[LucidMap] Grid: ","color":"aqua"},{"score":{"name":"#cols","objective":"lucid_map"}},{"text":" x "},{"score":{"name":"#rows","objective":"lucid_map"}},{"text":" = "},{"score":{"name":"#points","objective":"lucid_map"}},{"text":" stops; spacing 256 blocks"}]
