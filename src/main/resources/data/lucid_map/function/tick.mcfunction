# Timer is deliberately tick-based instead of /schedule so interval_ms can be
# changed as data and converted dynamically at scan start.
execute if score #running lucid_map matches 1 if entity @a[tag=lucid_map_target,limit=1] run scoreboard players add #timer lucid_map 1
execute if score #running lucid_map matches 1 if entity @a[tag=lucid_map_target,limit=1] if score #timer lucid_map >= #interval_ticks lucid_map run function lucid_map:advance
