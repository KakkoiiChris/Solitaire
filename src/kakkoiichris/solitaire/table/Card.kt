package kakkoiichris.solitaire.table

import kakkoiichris.hypergame.input.Input
import kakkoiichris.hypergame.media.Renderable
import kakkoiichris.hypergame.media.Renderer
import kakkoiichris.hypergame.state.StateManager
import kakkoiichris.hypergame.util.Time
import kakkoiichris.hypergame.util.math.Box
import kakkoiichris.hypergame.util.math.Vector
import kakkoiichris.hypergame.util.math.max
import kakkoiichris.hypergame.view.View
import kakkoiichris.solitaire.Game
import java.awt.*
import java.awt.image.BufferedImage

typealias Cards = MutableList<Card>

fun cards() =
    mutableListOf<Card>()

class Card(val suit: Suit, val rank: Rank, x: Double, y: Double, width: Double, height: Double) :
    Box(x, y, width, height), Renderable {
    companion object {
        private const val FLIP_SPEED = 10
        private const val EASE = 0.25

        val black = Color(0, 0, 0)
        val red = Color(200, 0, 0)

        fun generateDeck(width: Double, height: Double): Cards {
            val deck = cards()

            for (suit in Suit.entries) {
                for (rank in Rank.entries) {
                    deck += Card(suit, rank, 0.0, 0.0, width, height)
                }
            }

            return deck
        }
    }

    private var flipping = false
    private var flipDirection = FLIP_SPEED
    private var flipAmount = 0
    var faceUp = false//; private set

    var highlight = false

    private var moving = false
    var pickedUp = false

    var target: Vector = position
        set(value) {
            moving = true

            field = value
        }

    private val face = BufferedImage(width.toInt(), height.toInt(), BufferedImage.TYPE_INT_ARGB)
    private val back = BufferedImage(width.toInt(), height.toInt(), BufferedImage.TYPE_INT_ARGB)

    init {
        var graphics = face.graphics as Graphics2D

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        graphics.color = Color.WHITE
        graphics.fillRoundRect(0, 0, width.toInt(), height.toInt(), 10, 10)

        graphics.color = Color.BLACK
        graphics.drawRoundRect(0, 0, width.toInt(), height.toInt(), 10, 10)

        graphics.drawImage(
            suit.sprite,
            (width / 2).toInt() - suit.sprite.width / 2,
            (height / 2).toInt() - suit.sprite.height / 2,
            null
        )

        graphics.drawImage(suit.sprite, width.toInt() - 35, 5, 30, 30, null)

        graphics.drawImage(suit.sprite, 5, height.toInt() - 35, 30, 30, null)

        graphics.color = if (suit.isBlack) black else red
        graphics.font = Font("Century Gothic", Font.BOLD, 35)

        graphics.drawString(rank.text, 10, 35)

        graphics.drawString(rank.text, width.toInt() - 35, height.toInt() - 5)

        graphics.dispose()

        graphics = back.graphics as Graphics2D

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        graphics.composite = AlphaComposite.Src
        graphics.color = Color.WHITE
        graphics.fillRoundRect(2, 2, width.toInt() - 4, height.toInt() - 4, 10, 10)

        val cardBack = Game.resources.getFolder("img").getSprite("back")
        graphics.composite = AlphaComposite.SrcAtop
        graphics.drawImage(cardBack, 0, 0, width.toInt(), height.toInt(), null)

        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
        graphics.color = Color.WHITE
        graphics.stroke = BasicStroke(6F)
        graphics.drawRoundRect(3, 3, width.toInt() - 6, height.toInt() - 6, 10, 10)

        graphics.stroke = BasicStroke(1F)

        graphics.color = Color.BLACK
        graphics.drawRoundRect(0, 0, width.toInt(), height.toInt(), 10, 10)

        graphics.dispose()
    }

    fun flipUp() {
        if (!faceUp) {
            flipping = true
        }
    }

    fun flipDown() {
        if (faceUp) {
            flipping = true
        }
    }

    override fun update(view: View, manager: StateManager, time: Time, input: Input) {
        if (!pickedUp) {
            highlight = input.mouse in this
        }

        if (flipping) {
            if (flipAmount >= width / 2) {
                faceUp = !faceUp

                flipDirection = -FLIP_SPEED
            }

            if (flipAmount < 0) {
                flipDirection = FLIP_SPEED

                flipping = false
            }

            flipAmount += flipDirection
        }

        if (!flipping) {
            flipAmount = 0
        }

        if (moving) {
            position += (target - position) * max(time.delta, 1.0) * EASE
        }
    }

    override fun render(view: View, renderer: Renderer) {
        if (pickedUp) {
            renderer.color = Color(0, 0, 0, 100)

            renderer.fillRoundRect(
                x.toInt() + flipAmount + 10,
                y.toInt() + 10,
                width.toInt() - flipAmount * 2,
                height.toInt(),
                10,
                10
            )
        }

        renderer.drawImage(
            if (faceUp) face else back,
            x.toInt() + flipAmount,
            y.toInt(),
            width.toInt() - flipAmount * 2,
            height.toInt()
        )

        if (highlight) {
            renderer.color = Color(255, 255, 255, 100)

            renderer.fillRoundRect(
                x.toInt() + flipAmount,
                y.toInt(),
                width.toInt() - flipAmount * 2,
                height.toInt(),
                10,
                10
            )
        }
    }

    enum class Suit() {
        Spade,
        Diamond,
        Club,
        Heart;

        val isBlack = ordinal % 2 == 0

        val sprite = Game.resources.getFolder("img").getSprite(name.lowercase())
    }

    enum class Rank(val text: String) {
        Ace("A"),
        Two("2"),
        Three("3"),
        Four("4"),
        Five("5"),
        Six("6"),
        Seven("7"),
        Eight("8"),
        Nine("9"),
        Ten("10"),
        Jack("J"),
        Queen("Q"),
        King("K")
    }
}