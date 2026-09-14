tag @e[type=minecraft:item,tag=soulpack_recovery_item] remove soulpack_recovery_item
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:backpack run tag @s add soulpack_recovery_candidate
tag @e[type=minecraft:item,tag=soulpack_recovery_candidate,sort=nearest,limit=1] add soulpack_recovery_item
execute if entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] run function soulpack:recover_place
execute unless entity @e[type=minecraft:item,tag=soulpack_recovery_item,limit=1] run tellraw @s {"text":"No loaded dropped Backpack was found in your current dimension.","color":"red"}
tag @e[type=minecraft:item,tag=soulpack_recovery_candidate] remove soulpack_recovery_candidate
