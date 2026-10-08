#!/usr/bin/env bash
# End-to-end client test for the NeoForge build.
#
# Starts a dedicated server with RCON and a client on a virtual display that joins it,
# then drives a short scenario through RCON and xdotool: place machines, visit the Moon,
# summon a rocket on a launch pad and launch it. Screenshots of each step are written to
# $E2E_OUT_DIR (default: e2e). Fails if the client or server crashes or logs errors, or if
# the rocket does not launch.
#
# Requires: Xvfb (unless DISPLAY is set), xdotool, ImageMagick (import), python3.
set -uo pipefail

cd "$(dirname "$0")/../.."

OUT="${E2E_OUT_DIR:-e2e}"
RUN_DIR="neoforge/run"
SERVER_LOG="$OUT/server.log"
CLIENT_LOG="$OUT/client.log"
export RCON_PORT=25575 RCON_PASSWORD=gctest
RCON="python3 .github/scripts/rcon.py"

mkdir -p "$OUT" "$RUN_DIR"
status=0
pids=()

fail() {
    echo "E2E FAILED: $*"
    status=1
}

cleanup() {
    for pid in "${pids[@]}"; do
        kill -TERM -- "-$pid" 2>/dev/null
    done
    sleep 5
    for pid in "${pids[@]}"; do
        kill -KILL -- "-$pid" 2>/dev/null
    done
}
trap cleanup EXIT

# wait_for <file> <regex> <timeout-seconds>
wait_for() {
    local elapsed
    for ((elapsed = 0; elapsed < $3; elapsed += 2)); do
        grep -qE "$2" "$1" 2>/dev/null && return 0
        sleep 2
    done
    return 1
}

screenshot() {
    sleep "${2:-5}"
    import -window root "$OUT/$1.png" && echo "Screenshot: $OUT/$1.png"
}

# Prints the value of an entity NBT path, e.g. entity_data Tester Pos[1]
entity_data() {
    $RCON "data get entity $1 $2" | sed -E 's/^.*entity data: //'
}

if [[ -z "${DISPLAY:-}" ]]; then
    export DISPLAY=:99
    setsid Xvfb "$DISPLAY" -screen 0 1280x720x24 -nolisten tcp > "$OUT/xvfb.log" 2>&1 &
    pids+=($!)
    sleep 2
fi
export LIBGL_ALWAYS_SOFTWARE=1

# --- Server --------------------------------------------------------------------------------
rm -rf "$RUN_DIR/world"
echo "eula=true" > "$RUN_DIR/eula.txt"
cat > "$RUN_DIR/server.properties" <<PROPS
online-mode=false
server-port=25599
level-seed=galacticraft
spawn-protection=0
view-distance=6
simulation-distance=6
enable-rcon=true
rcon.port=$RCON_PORT
rcon.password=$RCON_PASSWORD
PROPS

setsid ./gradlew :neoforge:runServer --console=plain --no-daemon > "$SERVER_LOG" 2>&1 &
pids+=($!)
if ! wait_for "$SERVER_LOG" 'Done \([0-9.]+s\)!|Crash report|BUILD FAILED' 900 \
        || ! grep -qE 'Done \([0-9.]+s\)!' "$SERVER_LOG"; then
    fail "server did not start"
    tail -n 80 "$SERVER_LOG"
    exit 1
fi

# --- Client --------------------------------------------------------------------------------
cat > "$RUN_DIR/options.txt" <<OPTIONS
onboardAccessibility:false
renderDistance:6
simulationDistance:6
pauseOnLostFocus:false
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
soundCategory_master:0.0
OPTIONS

setsid ./gradlew :neoforge:runClient -PquickPlayServer=localhost:25599 --console=plain --no-daemon > "$CLIENT_LOG" 2>&1 &
pids+=($!)
if ! wait_for "$SERVER_LOG" 'Tester joined the game' 1200; then
    fail "client did not join the server"
    tail -n 80 "$CLIENT_LOG"
    exit 1
fi

# Keyboard input goes to the window under the pointer.
window=$(xdotool search --name 'Minecraft' | head -1)
eval "$(xdotool getwindowgeometry --shell "$window")"
xdotool mousemove $((X + WIDTH / 2)) $((Y + HEIGHT / 2))

# --- Scenario ------------------------------------------------------------------------------
ow="execute in minecraft:overworld run"
$RCON "op Tester" "gamemode creative Tester" "gamerule doDaylightCycle false" "time set day" \
    "effect give Tester minecraft:night_vision infinite 0 true" \
    "$ow forceload add -16 -16 16 16" "$ow tp Tester 0.5 100 0.5" \
    "$ow fill -8 64 -8 8 90 8 air" "$ow fill -8 63 -8 8 63 8 minecraft:stone" \
    "$ow setblock 3 64 -2 galacticraft:oxygen_collector" \
    "$ow setblock 3 64 0 galacticraft:circuit_fabricator" \
    "$ow setblock 3 64 2 galacticraft:basic_solar_panel" \
    "$ow tp Tester -1.5 64 0.5 -90 15"

# Geothermal generator on a vapor spout with sulfuric acid two blocks below, and a control
# generator on plain stone that must not produce anything.
$RCON "$ow setblock 3 63 4 air" "$ow setblock 3 62 4 galacticraft:sulfuric_acid" \
    "$ow setblock 3 64 4 galacticraft:vapor_spout" "$ow setblock 3 65 4 galacticraft:geothermal_generator" \
    "$ow setblock 3 64 6 galacticraft:geothermal_generator" > /dev/null
# Water electrolyzer preloaded with one bucket of water and energy. MachineLib saves fluid
# amounts in droplets (81000 per bucket) on both loaders.
$RCON "$ow setblock 3 64 -4 galacticraft:water_electrolyzer" \
    "$ow data merge block 3 64 -4 {EnergyStorage:30000L,FluidStorage:[{Resource:\"minecraft:water\",Amount:81000L},{},{}]}" > /dev/null
# Methane synthesizers with a bucket of hydrogen: one fed carbon fragments in the overworld, one
# on Mars with an atmospheric valve drawing carbon dioxide from the air.
mars="execute in galacticraft:mars run"
hydrogen_tank="FluidStorage:[{Resource:\"galacticraft:hydrogen\",Amount:81000L},{},{}]"
$RCON "$ow setblock 3 64 -6 galacticraft:methane_synthesizer" \
    "$ow data merge block 3 64 -6 {EnergyStorage:30000L,$hydrogen_tank,ItemStorage:[{},{},{},{Resource:\"galacticraft:carbon_fragments\",Amount:4},{}]}" \
    "$mars forceload add 0 0" "$mars setblock 0 200 0 galacticraft:methane_synthesizer" \
    "$mars data merge block 0 200 0 {EnergyStorage:30000L,$hydrogen_tank,ItemStorage:[{},{},{Resource:\"galacticraft:atmospheric_valve\",Amount:1},{},{}]}" > /dev/null
# Gas liquefiers: one with methane (becomes fuel) and one with oxygen (becomes liquid oxygen).
for liquefier in "3 64 -8 methane" "5 64 -8 oxygen"; do
    read -r x y z gas <<< "$liquefier"
    $RCON "$ow setblock $x $y $z galacticraft:gas_liquefier" \
        "$ow data merge block $x $y $z {EnergyStorage:30000L,FluidStorage:[{Resource:\"galacticraft:$gas\",Amount:81000L},{}]}" > /dev/null
done
# Two linked short range telepads: A (address 1) sends to B (address 2), which has no target.
for telepad in "-5 -5 1 2" "-5 5 2 -1"; do
    read -r x z address target <<< "$telepad"
    $RCON "$ow setblock $x 64 $z galacticraft:short_range_telepad" \
        "$ow data merge block $x 64 $z {EnergyStorage:30000L,TelepadAddress:$address,TelepadTargetAddress:$target}" > /dev/null
done
screenshot 01-machines

$RCON "dimtp galacticraft:moon Tester"
screenshot 02-moon 8
if [[ "$(entity_data Tester Dimension)" != '"galacticraft:moon"' ]]; then
    fail "player did not reach the Moon"
fi

# NBT path value of a block entity, or 0 when the path does not exist yet (machines only
# save energy and fluids once they have some).
# Usage: block_value <pos> <path> [execute prefix, default: the overworld]
block_value() {
    local value
    value=$($RCON "${3:-$ow} data get block $1 $2" | sed -nE 's/^.*block data: ([0-9]+)L?$/\1/p')
    echo "${value:-0}"
}
geothermal=$(block_value "3 65 4" EnergyStorage)
control=$(block_value "3 64 6" EnergyStorage)
echo "Geothermal generator energy: on spout=$geothermal, without spout=$control"
if ((geothermal <= 0)); then
    fail "geothermal generator on a vapor spout produced no energy"
fi
if ((control != 0)); then
    fail "geothermal generator without a vapor spout produced energy"
fi

water=$(block_value "3 64 -4" 'FluidStorage[0].Amount')
oxygen=$(block_value "3 64 -4" 'FluidStorage[1].Amount')
hydrogen=$(block_value "3 64 -4" 'FluidStorage[2].Amount')
echo "Water electrolyzer: water=$water oxygen=$oxygen hydrogen=$hydrogen"
if ((water >= 81000 || oxygen <= 0 || hydrogen <= oxygen)); then
    fail "water electrolyzer did not turn water into oxygen and hydrogen"
fi

fragments=$(block_value "3 64 -6" 'ItemStorage[3].Amount')
methane=$(block_value "3 64 -6" 'FluidStorage[2].Amount')
echo "Methane synthesizer (carbon fragments): methane=$methane fragments_left=$fragments"
if ((methane <= 0 || fragments >= 4)); then
    fail "methane synthesizer did not make methane from carbon fragments"
fi
mars_co2=$(block_value "0 200 0" 'FluidStorage[1].Amount' "$mars")
mars_methane=$(block_value "0 200 0" 'FluidStorage[2].Amount' "$mars")
echo "Methane synthesizer (Mars atmosphere): carbon_dioxide=$mars_co2 methane=$mars_methane"
if ((mars_methane <= 0)); then
    fail "methane synthesizer on Mars did not make methane from atmospheric carbon dioxide"
fi

for liquefier in "3 64 -8 galacticraft:fuel" "5 64 -8 galacticraft:liquid_oxygen"; do
    read -r x y z liquid <<< "$liquefier"
    amount=$(block_value "$x $y $z" 'FluidStorage[1].Amount')
    produced=$($RCON "$ow data get block $x $y $z FluidStorage[1].Resource" | sed -nE 's/^.*block data: "(.*)"$/\1/p')
    echo "Gas liquefier at $x $y $z: $amount of $produced"
    if ((amount <= 0)) || [[ "$produced" != *"$liquid"* ]]; then
        fail "gas liquefier at $x $y $z did not produce $liquid"
    fi
done

# Open telepad A's screen, then stand on it and wait to arrive on telepad B.
$RCON "$ow tp Tester -4.5 64 -3.5 180 60" > /dev/null
sleep 3
xdotool click 3
screenshot 03-telepad-screen 3
xdotool key Escape
sleep 1
$RCON "$ow tp Tester -4.5 64.45 -4.5" > /dev/null
arrived=0
for ((elapsed = 0; elapsed < 30; elapsed += 2)); do
    sleep 2
    z=$(entity_data Tester 'Pos[2]' | tr -d 'd')
    if [[ "$z" =~ ^-?[0-9.]+$ ]] && (($(printf '%.0f' "$z") >= 4)); then
        arrived=1
        break
    fi
done
telepad_energy=$(block_value "-5 64 -5" EnergyStorage)
echo "Short range telepad: arrived=$arrived after ${elapsed}s, z=$z, sender energy=$telepad_energy"
if ((arrived == 0)); then
    fail "short range telepad did not teleport the player"
fi

# 3x3 launch pad centred on (0, 64, 0) and a creative (fully fuelled) tier 1 rocket on it.
pad=(center:0:0 north:0:-1 south:0:1 west:-1:0 east:1:0 north_west:-1:-1 north_east:1:-1 south_west:-1:1 south_east:1:1)
for part in "${pad[@]}"; do
    IFS=: read -r name dx dz <<< "$part"
    $RCON "$ow setblock $dx 64 $dz galacticraft:rocket_launch_pad[part=$name]" > /dev/null
done
tier1='engine:"galacticraft:tier_1",fin:"galacticraft:tier_1",body:"galacticraft:tier_1",cone:"galacticraft:tier_1"'
$RCON "$ow tp Tester -4.5 64 0.5 -90 10" \
    "$ow summon galacticraft:rocket 0.5 64.1875 0.5 {Creative:1b,data:{$tier1}}" \
    "$ow ride Tester mount @e[type=galacticraft:rocket,limit=1]"
screenshot 04-rocket-on-pad

# Two presses of jump: the first arms the launch, the second ignites it.
for _ in 1 2; do
    xdotool keydown space
    sleep 0.3
    xdotool keyup space
    sleep 1
done

launched=0
for ((elapsed = 0; elapsed < 120; elapsed += 5)); do
    sleep 5
    height=$(entity_data Tester 'Pos[1]' | tr -d 'd')
    echo "t=${elapsed}s player y=$height"
    if [[ "$height" =~ ^[0-9.]+$ ]] && (($(printf '%.0f' "$height") > 300)); then
        launched=1
        break
    fi
done
screenshot 05-flight 1
if ((launched == 0)); then
    fail "rocket did not launch"
fi

# The celestial selection screen opens once the rocket leaves the atmosphere.
for ((elapsed = 0; elapsed < 120; elapsed += 5)); do
    [[ "$($RCON "$ow execute if entity @e[type=galacticraft:rocket]")" == *"Test passed"* ]] || break
    sleep 5
done
screenshot 06-celestial-screen 5

# --- Checks --------------------------------------------------------------------------------
if grep -qE 'Crash report|---- Minecraft Crash' "$CLIENT_LOG" "$SERVER_LOG"; then
    fail "crash report found"
fi
# The virtual display has no audio device or text-to-speech library, so sound engine and
# narrator errors are expected.
client_errors=$(grep -E '/ERROR\]' "$CLIENT_LOG" | grep -vE 'SoundEngine|OpenAL|mojang/Narrator' || true)
server_errors=$(grep -E '/ERROR\]' "$SERVER_LOG" || true)
if [[ -n "$client_errors" ]]; then
    fail "client logged errors"
    echo "$client_errors" | head -50
fi
if [[ -n "$server_errors" ]]; then
    fail "server logged errors"
    echo "$server_errors" | head -50
fi

((status == 0)) && echo "E2E PASSED"
exit "$status"
