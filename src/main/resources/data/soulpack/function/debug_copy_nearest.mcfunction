tag @e[type=minecraft:item,tag=soulpack_debug_item] remove soulpack_debug_item
function soulpack:recovery_tag_candidates
tag @e[type=minecraft:item,tag=soulpack_recovery_candidate,sort=nearest,limit=1] add soulpack_debug_item
scoreboard players set @s pk_restored 0
execute if entity @e[type=minecraft:item,tag=soulpack_debug_item,limit=1] unless items entity @s hotbar.8 * store success score @s pk_restored run item replace entity @s hotbar.8 from entity @e[type=minecraft:item,tag=soulpack_debug_item,limit=1] contents
execute if score @s pk_restored matches 1 run tellraw @s {"text":"Soulpack debug: copy-to-hotbar test succeeded. The ground item was NOT deleted.","color":"green"}
execute unless score @s pk_restored matches 1 run tellraw @s {"text":"Soulpack debug: copy-to-hotbar test failed. Leave hotbar slot 9 empty and check server log for command errors.","color":"red"}
tag @e[type=minecraft:item,tag=soulpack_recovery_candidate] remove soulpack_recovery_candidate
tag @e[type=minecraft:item,tag=soulpack_debug_item] remove soulpack_debug_item
