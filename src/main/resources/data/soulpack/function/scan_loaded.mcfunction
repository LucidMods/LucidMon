tellraw @s {"text":"Scanning loaded dropped Sophisticated Backpacks in your current dimension...","color":"aqua"}
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:backpack run data get entity @s Pos
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:copper_backpack run data get entity @s Pos
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:iron_backpack run data get entity @s Pos
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:gold_backpack run data get entity @s Pos
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:diamond_backpack run data get entity @s Pos
execute as @e[type=minecraft:item] if items entity @s contents sophisticatedbackpacks:netherite_backpack run data get entity @s Pos
