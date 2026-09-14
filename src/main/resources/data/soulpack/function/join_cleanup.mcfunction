# @s = player
# A valid first join has no death recovery pending.
execute if score @s pk_pending matches 0 run kill @e[type=minecraft:item,tag=soulpack,distance=..5]

# Remove only the known locked ghost form: soulbound iron starter bag with
# Soulpack's 32767-tick pickup lock. Ordinary dropped bags are untouched.
execute if score @s pk_pending matches 0 run kill @e[type=minecraft:item,distance=..5,nbt={PickupDelay:32767s,Item:{id:"sophisticatedbackpacks:iron_backpack",components:{"minecraft:custom_data":{soulbound:1b}}}}]
