package com.astraya.glyph

/**
 * Every picture the toy can show, drawn as 13x13 brightness frames.
 *
 * Art is plain text so it can be read (and previewed by tools/preview.py)
 * without running the app: '.' off, 'd' dim, 'B' blade, 'S' shard.
 * Everything stays inside a circle of radius 6.6 around the centre, in case
 * the matrix has rounded corners.
 */
object Slides {

    const val SIZE = 13

    private const val DIM = 60
    private const val BLADE = 160
    private const val SHARD = 255

    /** The Astraya A-emblem: two blades, the diamond shard brightest. */
    val EMBLEM = """
        ......B......
        .....B.B.....
        .....B.B.....
        ....BB.BB....
        ....B...B....
        ...BB.S.BB...
        ...B.SSS.B...
        ..BB..S..BB..
        ..B...S...B..
        .BB.......BB.
        .B.........B.
        .............
        .............
    """

    // Weather icons, 7 wide by 5 tall, drawn in the top half.
    private val SUN = """
        ..B.B..
        ...S...
        .BSSSB.
        ...S...
        ..B.B..
    """
    private val MOON = """
        ..SSS..
        .SS....
        .SS....
        .SS....
        ..SSS..
    """
    private val CLOUD = """
        .......
        ..BB...
        .BBBBB.
        BBBBBBB
        .......
    """
    private val RAIN = """
        ..BB...
        .BBBBB.
        BBBBBBB
        .......
        S.S.S.S
    """
    private val SNOW = """
        ..BB...
        .BBBBB.
        BBBBBBB
        .......
        .S..S..
    """
    private val STORM = """
        ..BB...
        .BBBBB.
        BBBBBBB
        ...SS..
        ..SS...
    """
    private val FOG = """
        BBBBBBB
        .......
        BBBBBBB
        .......
        BBBBBBB
    """

    /** 3x5 digits (3-bit rows, top to bottom), plus a minus sign. */
    private val FONT = mapOf(
        '0' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111),
        '1' to intArrayOf(0b010, 0b110, 0b010, 0b010, 0b111),
        '2' to intArrayOf(0b111, 0b001, 0b111, 0b100, 0b111),
        '3' to intArrayOf(0b111, 0b001, 0b111, 0b001, 0b111),
        '4' to intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001),
        '5' to intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111),
        '6' to intArrayOf(0b111, 0b100, 0b111, 0b101, 0b111),
        '7' to intArrayOf(0b111, 0b001, 0b010, 0b010, 0b010),
        '8' to intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111),
        '9' to intArrayOf(0b111, 0b101, 0b111, 0b001, 0b111),
        '-' to intArrayOf(0b000, 0b000, 0b111, 0b000, 0b000),
    )

    fun emblem(): IntArray = IntArray(SIZE * SIZE).also { stamp(it, EMBLEM, 0, 0) }

    /** Hours on top, minutes below. */
    fun time(hours: Int, minutes: Int): IntArray {
        val frame = IntArray(SIZE * SIZE)
        text(frame, hours.toString(), y = 1)
        text(frame, "%02d".format(minutes), y = 7)
        return frame
    }

    /** An icon for the sky on top, the temperature in °C below. */
    fun weather(tempC: Int, code: Int, isDay: Boolean): IntArray {
        val frame = IntArray(SIZE * SIZE)
        stamp(frame, iconFor(code, isDay), x = 3, y = 1)
        val label = tempC.coerceIn(-99, 99).toString()
        val left = text(frame, label, y = 7, reserve = 2)
        // Degree mark: one pixel, top-right of the number.
        set(frame, left + label.length * 4, 7, BLADE)
        return frame
    }

    /** WMO weather codes, as Open-Meteo reports them. */
    private fun iconFor(code: Int, isDay: Boolean): String = when (code) {
        0, 1 -> if (isDay) SUN else MOON
        2, 3 -> CLOUD
        45, 48 -> FOG
        in 51..67, in 80..82 -> RAIN
        in 71..77, 85, 86 -> SNOW
        in 95..99 -> STORM
        else -> CLOUD
    }

    /** Draws [s] centred on row [y]; [reserve] keeps columns free on the right. Returns the left x. */
    private fun text(frame: IntArray, s: String, y: Int, reserve: Int = 0): Int {
        val width = s.length * 4 - 1 + reserve
        val left = (SIZE - width) / 2
        s.forEachIndexed { i, ch ->
            val glyph = FONT[ch] ?: return@forEachIndexed
            for (row in 0 until 5) for (col in 0 until 3) {
                if (glyph[row] shr (2 - col) and 1 == 1) set(frame, left + i * 4 + col, y + row, SHARD)
            }
        }
        return left
    }

    private fun stamp(frame: IntArray, art: String, x: Int, y: Int) {
        art.trimIndent().lines().forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                val v = when (ch) {
                    'd' -> DIM
                    'B' -> BLADE
                    'S' -> SHARD
                    else -> 0
                }
                if (v > 0) set(frame, x + col, y + row, v)
            }
        }
    }

    private fun set(frame: IntArray, x: Int, y: Int, v: Int) {
        if (x in 0 until SIZE && y in 0 until SIZE) frame[y * SIZE + x] = v
    }
}
