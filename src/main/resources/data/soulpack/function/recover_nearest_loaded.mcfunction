function soulpack:recovery_tag_candidates
tag @e[type=minecraft:item,tag=soulpack_recovery_item] remove soulpack_recovery_item
tag @e[type=minecraft:item,tag=soulpack_recovery_candidate,sort=nearest,limit=1] add soulpack_recovery_item
execute if entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] run function soulpack:recover_place
execute unless entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] run tellraw @s {"text":"No loaded dropped Sophisticated Backpack was found in your current dimension.","color":"red"}
tag @e[type=minecraft:item,tag=soulpack_recovery_candidate] remove soulpack_recovery_candidate
