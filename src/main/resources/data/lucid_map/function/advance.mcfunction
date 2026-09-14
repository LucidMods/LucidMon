# Branch exactly once. #advanced prevents the same tick from moving twice if
# a horizontal move happens to land on the last column.
scoreboard players set #advanced lucid_map 0
execute if score #col lucid_map < #last_col lucid_map run function lucid_map:advance_horizontal
execute if score #advanced lucid_map matches 0 if score #row lucid_map < #last_row lucid_map run function lucid_map:advance_row
execute if score #advanced lucid_map matches 0 run function lucid_map:finish
