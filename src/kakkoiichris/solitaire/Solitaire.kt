package kakkoiichris.solitaire

import kakkoiichris.hypergame.Game
import kakkoiichris.hypergame.input.Button
import kakkoiichris.hypergame.input.Input
import kakkoiichris.hypergame.input.Key
import kakkoiichris.hypergame.media.Renderer
import kakkoiichris.hypergame.util.Time
import kakkoiichris.hypergame.util.filesystem.ResourceManager
import kakkoiichris.hypergame.util.math.Box
import kakkoiichris.hypergame.util.math.Vector
import kakkoiichris.hypergame.view.View
import kakkoiichris.solitaire.table.*
import java.awt.Color
import java.awt.Font
import kotlin.concurrent.thread
import kotlin.math.min

object Solitaire : Game {
    private const val HEADER_HEIGHT = 50
    private const val DEAL_DELAY = 0.2

    val resources = ResourceManager("/resources")

    private val felt = resources.getFolder("img").getSprite("felt")

    private lateinit var allCards: Cards

    private lateinit var foundations: Array<CardSpace.Foundation>
    private lateinit var hand: CardSpace.Hand
    private lateinit var deck: CardSpace.Deck
    private lateinit var depots: Array<CardSpace.Depot>

    private val heldCards = cards()

    private var origin: CardSpace? = null

    private val particles = particles()

    private var timer = 0.0

    private var moves = 0

    private var creatingTableau = true
    private var dealStart = 0
    private var dealIndex = dealStart
    private var dealTimer = 0.0
    private var dealing = true
    private var flipping = true

    override fun init(view: View) {
        val cols = 7
        val border = 25

        val cardWidth = (view.width - ((cols + 1) * border)) / 7.0
        val cardHeight = (cardWidth / 2.5) * 3.5

        foundations = Array(4) { i ->
            CardSpace.Foundation(
                (i * cardWidth + (i + 1) * border),
                HEADER_HEIGHT + border.toDouble(),
                cardWidth,
                cardHeight,
                Card.Suit.entries[i]
            )
        }

        hand = CardSpace.Hand(
            4.1 * cardWidth + 6 * border,
            HEADER_HEIGHT + border.toDouble(),
            cardWidth,
            cardHeight
        )

        deck = CardSpace.Deck(
            6.0 * cardWidth + 7 * border,
            HEADER_HEIGHT + border.toDouble(),
            cardWidth,
            cardHeight
        )

        depots = Array(7) { i ->
            CardSpace.Depot(
                ((i * cardWidth) + ((i + 1) * border)),
                HEADER_HEIGHT + ((border * 2) + cardHeight), cardWidth, cardHeight
            )
        }

        allCards = Card.generateDeck(cardWidth, cardHeight).apply { shuffle() }

        deck.putAll(allCards)
    }

    private fun isVictory() =
        foundations.all { it.isFull() }

    override fun update(view: View, time: Time, input: Input) {
        updateCards(view, time, input)

        if (creatingTableau) {
            updateCreateTableau(time)

            return
        }

        updateInput(view, input)

        updateParticles(view, time, input)

        timer += time.seconds
    }

    private fun updateCards(view: View, time: Time, input: Input) {
        depots.forEach { it.update(view, this, time, input) }

        foundations.forEach { it.update(view, this, time, input) }

        hand.update(view, this, time, input)

        deck.update(view, this, time, input)

        heldCards.forEach { it.update(view, this, time, input) }
    }

    private fun updateCreateTableau(time: Time) {
        dealTimer += time.seconds

        if (dealTimer < DEAL_DELAY) return

        dealTimer -= DEAL_DELAY

        if (dealing) {
            val card = deck.take(false)!!

            allCards += card
            depots[dealIndex].place(card)

            dealIndex++

            if (dealIndex == depots.size) {
                dealStart++

                dealIndex = dealStart
            }

            if (dealStart == depots.size) {
                dealing = false
            }

            return
        }

        depots.forEach { it.flipTopCard() }

        creatingTableau = false
    }

    private fun updateInput(view: View, input: Input) {
        if (input.keyDown(Key.ESCAPE)) {
            view.close()

            return
        }

        if (input.keyDown(Key.R)) {
            deck.shuffle()
        }

        cardSpaceInput(input)
    }

    private fun cardSpaceInput(input: Input) {
        val mousePoint = input.mouse

        if (input.buttonDown(Button.LEFT) && heldCards.isEmpty()) {
            when {
                // ACES
                foundations.any { mousePoint in it }        -> {
                    foundations.firstOrNull { mousePoint in it }?.let { ace ->
                        val card = ace.take(true) ?: return

                        allCards += card
                        heldCards += card

                        origin = ace
                    }
                }

                // HAND
                hand.inAll(mousePoint) && hand.isNotEmpty() -> {
                    val card = hand.take(true) ?: return

                    allCards += card
                    heldCards += card

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

                                allCards += card
                                hand.place(card)
                            }
                        }

                        moves++
                    }
                    else {
                        for (i in hand.indices.reversed()) {
                            val card = hand.removeAt(i)

                            card.flipDown()

                            allCards += card
                            deck.place(card)
                        }
                    }

                    origin = deck
                }

                // STACKS
                depots.any { it.inAll(mousePoint) }         -> {
                    depots.firstOrNull { it.inAll(mousePoint) }?.let { stack ->
                        heldCards.addAll(stack.take(mousePoint))

                        origin = stack
                    }
                }
            }
        }

        if (input.buttonUp(Button.LEFT) && heldCards.isNotEmpty()) {
            for (ace in foundations) {
                if (mousePoint !in ace || heldCards.size != 1 || !ace.accepts(heldCards[0])) {
                    continue
                }

                ace.place(heldCards.removeAt(0))

                heldCards.clear()

                if (origin is CardSpace.Depot) {
                    origin.let { it!!.flipTopCard() }
                }

                moves++

                spawnParticles(ace)

                return
            }

            for (stack in depots) {
                if (!stack.inAll(mousePoint) || !stack.accepts(heldCards[0])) {
                    continue
                }

                stack.placeAll(heldCards)

                heldCards.clear()

                if (origin is CardSpace.Depot) {
                    origin.let { it!!.flipTopCard() }
                }

                moves++

                return
            }

            if (!(origin == null || origin is CardSpace.Deck)) {
                origin.let { it!!.placeAll(heldCards) }

                heldCards.clear()
            }

            origin = null
        }

        if (input.buttonHeld(Button.LEFT) && heldCards.isNotEmpty()) {
            for (i in heldCards.indices) {
                heldCards[i].target = mousePoint - Vector(
                    heldCards[0].width / 2,
                    heldCards[0].height / 2
                ) + Vector(y = i * CardSpace.Depot.MAX_OFFSET)
            }
        }

        if (!input.buttonDown(Button.RIGHT) || !heldCards.isEmpty()) {
            return
        }

        when {
            // ACES
            foundations.any { mousePoint in it }        -> {
                foundations.firstOrNull { mousePoint in it }?.let { ace ->
                    if (ace.isNotEmpty()) {
                        val card = ace.last()

                        depots.firstOrNull { s -> s.accepts(card) }?.let { stack ->
                            stack.place(ace.take(true))

                            moves++
                        }
                    }
                }
            }

            // HAND
            hand.inAll(mousePoint) && hand.isNotEmpty() -> {
                val card = hand.last()

                foundations.firstOrNull { s -> s.accepts(card) }
                    ?.let { ace ->
                        ace.place(hand.take(true))

                        spawnParticles(ace)

                        moves++
                    }
                    ?: depots.firstOrNull { s -> s.accepts(card) }?.let { stack ->
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
            depots.any { it.inAll(mousePoint) }         -> {
                depots.firstOrNull { it.inAll(mousePoint) }?.let { stack ->
                    val cards = stack.take(mousePoint)

                    if (cards.isEmpty()) return@let

                    if (cards.size == 1) {
                        val card = cards.first()

                        foundations.firstOrNull { s -> s.accepts(card) }
                            ?.let { foundation ->
                                foundation.place(card)

                                stack.flipTopCard()

                                spawnParticles(foundation)

                                moves++

                                return
                            }
                    }

                    depots
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

    private fun spawnParticles(foundation: CardSpace.Foundation) =
        particles.addAll(List(20) { Particle(foundation.center, foundation.suit) })

    private fun updateParticles(view: View, time: Time, input: Input) {
        particles.forEach { it.update(view, this, time, input) }

        particles.removeIf { it.isDead }
    }

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

        depots.forEach { it.render(view, this, renderer) }

        foundations.forEach { it.render(view, this, renderer) }

        deck.render(view, this, renderer)

        hand.render(view, this, renderer)

        allCards.forEach { it.render(view, this, renderer) }

        heldCards.forEach { it.render(view, this, renderer) }

        particles.forEach { it.render(view, this, renderer) }

        if (isVictory()) {
            renderer.color = Color(0, 0, 0, 200)
            renderer.fillRect(0, 0, view.width, view.height)
            renderer.color = Color.WHITE
            renderer.font = Font("Monospaced", Font.BOLD, 100)
            renderer.drawString("YOU WIN!!!!!", view.bounds)
        }
    }

    override fun halt(view: View) = Unit
}