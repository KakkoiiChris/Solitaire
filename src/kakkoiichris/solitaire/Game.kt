package kakkoiichris.solitaire

import kakkoiichris.hypergame.input.Button
import kakkoiichris.hypergame.input.Input
import kakkoiichris.hypergame.input.Key
import kakkoiichris.hypergame.media.Renderer
import kakkoiichris.hypergame.state.State
import kakkoiichris.hypergame.state.StateManager
import kakkoiichris.hypergame.util.Time
import kakkoiichris.hypergame.util.filesystem.ResourceManager
import kakkoiichris.hypergame.util.math.Box
import kakkoiichris.hypergame.util.math.Vector
import kakkoiichris.hypergame.view.Display
import kakkoiichris.hypergame.view.View
import kakkoiichris.solitaire.table.*
import java.awt.Color
import java.awt.Font
import kotlin.concurrent.thread
import kotlin.math.min

object Game : State {
    private const val HEADER_HEIGHT = 50

    val resources = ResourceManager("/resources")

    private val felt = resources.getFolder("img").getSprite("felt")

    private lateinit var aces: Array<CardSpace.Ace>
    private lateinit var hand: CardSpace.Hand
    private lateinit var deck: CardSpace.Deck
    private lateinit var stacks: Array<CardSpace.Stack>

    private val heldCards = cards()

    private var origin: CardSpace? = null

    private val particles = particles()

    private var timer = 0.0

    private var moves = 0

    @JvmStatic
    fun main(args: Array<String>) {
        val icon = resources.getFolder("img").getSprite("icon")

        val display = Display(1200, 900, title = "Solitaire", icon = icon)

        display.manager.push(Game)

        display.open()
    }

    override fun swapTo(view: View) {
        val cols = 7
        val border = 25

        val cardWidth = (view.width - ((cols + 1) * border)) / 7
        val cardHeight = (cardWidth / 2.5) * 3.5

        aces = Array(4) { i ->
            CardSpace.Ace(
                (i * cardWidth + (i + 1) * border).toDouble(),
                HEADER_HEIGHT + border.toDouble(),
                cardWidth.toDouble(),
                cardHeight,
                Card.Suit.entries[i]
            )
        }

        hand = CardSpace.Hand(
            4.1 * cardWidth + 6 * border,
            HEADER_HEIGHT + border.toDouble(),
            cardWidth.toDouble(),
            cardHeight
        )

        deck = CardSpace.Deck(
            6.0 * cardWidth + 7 * border,
            HEADER_HEIGHT + border.toDouble(),
            cardWidth.toDouble(),
            cardHeight
        )

        val allCards = Card.generateDeck(cardWidth.toDouble(), cardHeight).apply { shuffle() }

        stacks = Array(7) { i ->
            val stack = CardSpace.Stack(
                ((i * cardWidth) + ((i + 1) * border)).toDouble(),
                HEADER_HEIGHT + ((border * 2) + cardHeight), cardWidth.toDouble(), cardHeight
            )

            for (j in 0..i) {
                stack.put(allCards.removeAt(0))
            }

            stack.flipTopCard()

            stack
        }

        deck.putAll(allCards)
    }

    override fun swapFrom(view: View) {
    }

    private fun isVictory(): Boolean {
        for (ace in aces) {
            if (ace.count != 13) {
                return false
            }
        }

        return true
    }

    override fun update(view: View, manager: StateManager, time: Time, input: Input) {
        stacks.forEach { it.update(view, manager, time, input) }

        aces.forEach { it.update(view, manager, time, input) }

        hand.update(view, manager, time, input)

        deck.update(view, manager, time, input)

        heldCards.forEach { it.update(view, manager, time, input) }

        if (input.keyDown(Key.ESCAPE)) {
            view.close()

            return
        }

        if (input.keyDown(Key.R)) {
            deck.shuffle()
        }

        cardSpaceInput(input)

        particles.forEach { it.update(view, manager, time, input) }

        particles.removeIf { it.isDead }

        timer += time.seconds
    }

    private fun cardSpaceInput(input: Input) {
        val mousePoint = input.mouse

        if (input.buttonDown(Button.LEFT) && heldCards.isEmpty()) {
            when {
                // ACES
                aces.any { mousePoint in it }               -> {
                    aces.firstOrNull { mousePoint in it }?.let { ace ->
                        heldCards.add(ace.take(true) ?: return)

                        origin = ace
                    }
                }

                // HAND
                hand.inAll(mousePoint) && hand.isNotEmpty() -> {
                    heldCards.add(hand.take(true) ?: return)

                    origin = hand
                }

                // DECK
                mousePoint in deck                          -> {
                    if (deck.isNotEmpty()) {
                        val limit = min(deck.count, 3)

                        repeat(limit) { i ->
                            thread {
                                Thread.sleep((i * 100).toLong())

                                val card = deck.take(false) ?: return@thread

                                card.flipUp()

                                hand.place(card)
                            }
                        }

                        moves++
                    }
                    else {
                        for (i in hand.indices.reversed()) {
                            val card = hand.removeAt(i)

                            card.flipDown()

                            deck.place(card)
                        }
                    }

                    origin = deck
                }

                // STACKS
                stacks.any { it.inAll(mousePoint) }         -> {
                    stacks.firstOrNull { it.inAll(mousePoint) }?.let { stack ->
                        heldCards.addAll(stack.take(mousePoint))

                        origin = stack
                    }
                }
            }
        }

        if (input.buttonUp(Button.LEFT) && heldCards.isNotEmpty()) {
            for (ace in aces) {
                if (mousePoint in ace && heldCards.size == 1 && ace.accepts(heldCards[0])) {
                    ace.place(heldCards.removeAt(0))

                    heldCards.clear()

                    if (origin is CardSpace.Stack) {
                        origin.let { it!!.flipTopCard() }
                    }

                    moves++

                    spawnParticles(ace)

                    return
                }
            }

            for (stack in stacks) {
                if (stack.inAll(mousePoint) && stack.accepts(heldCards[0])) {
                    stack.placeAll(heldCards)

                    heldCards.clear()

                    if (origin is CardSpace.Stack) {
                        origin.let { it!!.flipTopCard() }
                    }

                    moves++

                    return
                }
            }

            if (!(origin == null || origin is CardSpace.Deck)) {
                origin.let { it!!.placeAll(heldCards) }

                heldCards.clear()
            }

            origin = null
        }

        if (input.buttonHeld(Button.LEFT)) {
            if (heldCards.isNotEmpty()) {
                for (i in heldCards.indices) {
                    heldCards[i].target = mousePoint - Vector(
                        heldCards[0].width / 2,
                        heldCards[0].height / 2
                    ) + Vector(y = i * CardSpace.Stack.MAX_OFFSET)
                }
            }
        }

        if (input.buttonDown(Button.RIGHT) && heldCards.isEmpty()) {
            when {
                // ACES
                aces.any { mousePoint in it }               -> {
                    aces.firstOrNull { mousePoint in it }?.let { ace ->
                        if (ace.isNotEmpty()) {
                            val card = ace.last()

                            stacks.firstOrNull { s -> s.accepts(card) }?.let { stack ->
                                stack.place(ace.take(true))

                                moves++
                            }
                        }
                    }
                }

                // HAND
                hand.inAll(mousePoint) && hand.isNotEmpty() -> {
                    val card = hand.last()

                    aces.firstOrNull { s -> s.accepts(card) }
                        ?.let { ace ->
                            ace.place(hand.take(true))

                            spawnParticles(ace)

                            moves++
                        }
                        ?: stacks.firstOrNull { s -> s.accepts(card) }?.let { stack ->
                            stack.place(hand.take(true))

                            moves++
                        }
                        ?: return
                }

                mousePoint in deck                          -> {
                    if (deck.isNotEmpty()) {
                        val card = deck.take(false)!!

                        card.flipUp()

                        hand.place(card)

                        moves++
                    }
                    else {
                        for (i in hand.indices.reversed()) {
                            val card = hand.removeAt(i)

                            card.flipDown()

                            deck.place(card)
                        }
                    }
                }

                // STACKS
                stacks.any { it.inAll(mousePoint) }         -> {
                    stacks.firstOrNull { it.inAll(mousePoint) }?.let { stack ->
                        val cards = stack.take(mousePoint) as Cards

                        if (cards.isNotEmpty()) {
                            if (cards.size == 1) {
                                val card = cards.first()

                                aces.firstOrNull { s -> s.accepts(card) }
                                    ?.let { ace ->
                                        ace.place(card)

                                        stack.flipTopCard()

                                        spawnParticles(ace)

                                        moves++

                                        return
                                    }
                            }

                            stacks
                                .filter { it !== stack }
                                .firstOrNull { it.accepts(cards.first()) }
                                ?.let { target ->
                                    target.placeAll(cards)

                                    stack.flipTopCard()

                                    moves++
                                }
                                ?: stack.placeAll(cards)
                        }
                    }
                }
            }
        }
    }

    private fun spawnParticles(ace: CardSpace.Ace) =
        particles.addAll(List(20) { Particle(ace.center, ace.suit) })

    override fun render(view: View, renderer: Renderer) {
        for (y in 0 until view.height step felt.height) {
            for (x in 0 until view.width step felt.width) {
                renderer.drawImage(felt, x, y)
            }
        }

        val headerBox = Box(0.0, 0.0, view.width.toDouble(), HEADER_HEIGHT.toDouble())

        renderer.font = Font("Monospaced", Font.BOLD, 40)
        renderer.color = Color.WHITE
        renderer.drawString("Moves: $moves", headerBox, xAlign = 0.1)
        renderer.drawString(
            "Time: %02d:%02d".format(timer.toInt() / 60, timer.toInt() % 60),
            headerBox,
            xAlign = 0.9
        )

        stacks.forEach { it.render(view, renderer) }

        aces.forEach { it.render(view, renderer) }

        deck.render(view, renderer)

        hand.render(view, renderer)

        heldCards.forEach { it.render(view, renderer) }

        particles.forEach { it.render(view, renderer) }

        if (isVictory()) {
            renderer.color = Color(0, 0, 0, 200)
            renderer.fillRect(0, 0, view.width, view.height)
            renderer.color = Color.WHITE
            renderer.font = Font("Monospaced", Font.BOLD, 100)
            renderer.drawString("YOU WIN!!!!!", view.bounds)
        }
    }

    override fun halt(view: View) {
    }
}