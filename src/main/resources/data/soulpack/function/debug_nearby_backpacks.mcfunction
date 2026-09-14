tellraw @s {"text":"Soulpack debug: loaded Sophisticated Backpacks within 12 blocks of you:","color":"aqua"}
execute as @e[type=minecraft:item,distance=..12] if items entity @s contents sophisticatedbackpacks:backpack run data get entity @s Item
execute as @e[type=minecraft:item,distance=..12] if items entity @s contents sophisticatedbackpacks:copper_backpack run data get entity @s Item
execute as @e[type=minecraft:item,distance=..12] if items entity @s contents sophisticatedbackpacks:iron_backpack run data get entity @s Item
execute as @e[type=minecraft:item,distance=..12] if items entity @s contents sophisticatedbackpacks:gold_backpack run data get entity @s Item
execute as @e[type=minecraft:item,distance=..12] if items entity @s contents sophisticatedbackpacks:diamond_backpack run data get entity @s Item
execute as @e[type=minecraft:item,distance=..12] if items entity @s contents sophisticatedbackpacks:netherite_backpack run data get entity @s Item
