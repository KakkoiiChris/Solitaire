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
import kotlin.math.min

object Solitaire : Game {
    private const val HEADER_HEIGHT = 50
    private const val MOVE_DELAY = 0.1

    val resources = ResourceManager("/resources")

    private val felt = resources
        .getFolder("img")
        .getSprite("felt")

    private val allCards = cards()

    private lateinit var foundations: Array<CardSpace.Foundation>
    private lateinit var hand: CardSpace.Hand
    private lateinit var deck: CardSpace.Deck
    private lateinit var depots: Array<CardSpace.Depot>

    private val heldCards = cards()

    private var origin: CardSpace? = null

    private val particles = particles()

    private var gameTime = 0.0
    private var moves = 0

    private var state = State.DEAL

    private var moveTimer = 0.0

    private var dealStart = 0
    private var dealIndex = dealStart

    private val cardsToReturn = cards()

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

        allCards += Card.generateDeck(cardWidth, cardHeight).apply { shuffle() }

        deck.putAll(allCards)
    }

    private fun isVictory() =
        foundations.all { it.isFull() }

    override fun update(view: View, time: Time, input: Input) {
        when (state) {
            State.DEAL  -> updateDeal(view, time, input)
            State.PLAY  -> updatePlay(view, time, input)
            State.WIN   -> updateWin(view, time, input)
            State.LOSS  -> updateLoss(view, time, input)
            State.RESET -> updateReset(view, time, input)
        }
    }

    private fun updateDeal(view: View, time: Time, input: Input) {
        updateCards(view, time, input)

        moveTimer += time.seconds

        if (moveTimer < MOVE_DELAY) return

        moveTimer -= MOVE_DELAY

        val card = deck.take(false) ?: error("No card to deal?!")

        allCards += card
        depots[dealIndex].place(card)

        dealIndex++

        if (dealIndex == depots.size) {
            dealStart++

            dealIndex = dealStart
        }

        if (dealStart != depots.size) return

        depots.forEach { it.flipTopCard() }

        state = State.PLAY
    }

    private fun updatePlay(view: View, time: Time, input: Input) {
        gameTime += time.seconds

        updateCards(view, time, input)

        updateInput(view, input)

        updateParticles(view, time, input)

        if (!isVictory()) return

        state = State.WIN
    }

    private fun updateWin(view: View, time: Time, input: Input) {
        updateCards(view, time, input)

        updateParticles(view, time, input)

        if (!input.keyDown(Key.SPACE)) return

        for (foundation in foundations) {
            cardsToReturn += foundation.cards
            foundation.clear()
        }

        state = State.RESET
    }

    private fun updateLoss(view: View, time: Time, input: Input) {
        updateCards(view, time, input)

        updateParticles(view, time, input)

        if (!input.keyDown(Key.SPACE)) return

        for (depot in depots) {
            cardsToReturn += depot.cards
            depot.clear()
        }

        for (foundation in foundations) {
            cardsToReturn += foundation.cards
            foundation.clear()
        }

        cardsToReturn += hand.cards
        hand.clear()

        state = State.RESET
    }

    private fun updateReset(view: View, time: Time, input: Input) {
        updateCards(view, time, input)

        moveTimer += time.seconds

        if (moveTimer < MOVE_DELAY) return

        moveTimer -= MOVE_DELAY

        val card = cardsToReturn.removeLast()

        card.flipDown()

        moveCardToFront(card)
        deck.place(card)

        if (cardsToReturn.isNotEmpty()) return

        gameTime = 0.0

        deck.shuffle()

        dealStart = 0
        dealIndex = dealStart

        state = State.DEAL
    }

    private fun updateCards(view: View, time: Time, input: Input) {
        depots.forEach { it.update(view, this, time, input) }

        foundations.forEach { it.update(view, this, time, input) }

        hand.update(view, this, time, input)

        deck.update(view, this, time, input)

        heldCards.forEach { it.update(view, this, time, input) }
    }

    private fun moveCardToFront(card: Card) {
        allCards -= card
        allCards += card
    }

    private fun moveCardsToFront(cards: Cards) {
        cards.forEach(::moveCardToFront)
    }

    private fun updateInput(view: View, input: Input) {
        if (input.keyDown(Key.ESCAPE)) {
            view.close()

            return
        }

        if (input.keyDown(Key.R)) {
            state = State.LOSS
        }

        cardSpaceInput(input)
    }

    private fun cardSpaceInput(input: Input) {
        val mousePoint = input.mouse

        when {
            input.buttonDown(Button.LEFT) && heldCards.isEmpty()    -> takeCards(mousePoint)

            input.buttonUp(Button.LEFT) && heldCards.isNotEmpty()   -> moveCards(mousePoint)

            input.buttonHeld(Button.LEFT) && heldCards.isNotEmpty() -> placeCards(mousePoint)

            input.buttonDown(Button.RIGHT) && heldCards.isEmpty()   -> cheatCards(mousePoint)
        }
    }

    private fun takeCards(mousePoint: Vector) {
        when {
            // FOUNDATIONS
            foundations.any { mousePoint in it }        -> takeFoundation(mousePoint)

            // HAND
            hand.inAll(mousePoint) && hand.isNotEmpty() -> takeHand()

            // DECK
            mousePoint in deck                          -> takeDeck()

            // STACKS
            depots.any { it.inAll(mousePoint) }         -> takeDepot(mousePoint)
        }
    }

    private fun takeFoundation(mousePoint: Vector) {
        foundations.firstOrNull { mousePoint in it }?.let { ace ->
            val card = ace.take(true) ?: return

            moveCardToFront(card)
            heldCards += card

            origin = ace
        }
    }

    private fun takeHand() {
        val card = hand.take(true) ?: return

        moveCardToFront(card)
        heldCards += card

        origin = hand
    }

    private fun takeDeck() {
        if (deck.isNotEmpty()) {
            val limit = min(deck.count, 3)

            repeat(limit) { i ->
                val card = deck.take(false) ?: TODO()

                card.flipUp()

                moveCardToFront(card)
                hand.place(card)
            }

            moves++
        }
        else {
            for (i in hand.indices.reversed()) {
                val card = hand.removeAt(i)

                card.flipDown()

                moveCardToFront(card)
                deck.place(card)
            }
        }

        origin = deck
    }

    private fun takeDepot(mousePoint: Vector) {
        depots.firstOrNull { it.inAll(mousePoint) }?.let { stack ->
            val cards = stack.take(mousePoint)

            moveCardsToFront(cards)
            heldCards.addAll(cards)

            origin = stack
        }
    }

    private fun moveCards(mousePoint: Vector) {
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

    private fun placeCards(mousePoint: Vector) {
        for (i in heldCards.indices) {
            heldCards[i].target = mousePoint - Vector(
                heldCards[0].width / 2,
                heldCards[0].height / 2
            ) + Vector(y = i * CardSpace.Depot.MAX_OFFSET)
        }
    }

    private fun cheatCards(mousePoint: Vector) {
        when {
            // ACES
            foundations.any { mousePoint in it }        -> {
                foundations.firstOrNull { mousePoint in it }?.let { ace ->
                    if (ace.isNotEmpty()) {


                        depots.firstOrNull { s -> s.accepts(ace.last()) }?.let { stack ->
                            val card = ace.take(true) ?: TODO()

                            moveCardToFront(card)
                            stack.place(card)

                            moves++
                        }
                    }
                }
            }

            // HAND
            hand.inAll(mousePoint) && hand.isNotEmpty() -> {
                foundations.firstOrNull { s -> s.accepts(hand.last()) }
                    ?.let { ace ->
                        val card = hand.take(true) ?: TODO()

                        moveCardToFront(card)
                        ace.place(card)

                        spawnParticles(ace)

                        moves++
                    }
                    ?: depots.firstOrNull { s -> s.accepts(hand.last()) }?.let { stack ->
                        val card = hand.take(true) ?: TODO()

                        moveCardToFront(card)
                        stack.place(card)

                        moves++
                    }
                    ?: return
            }

            mousePoint in deck                          -> {
                if (deck.isNotEmpty()) {
                    val card = deck.take(false)!!

                    card.flipUp()

                    moveCardToFront(card)
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
                depots.firstOrNull { it.inAll(mousePoint) }?.let { depot ->
                    val cards = depot.take(mousePoint)

                    if (cards.isEmpty()) return@let

                    if (cards.size == 1) {
                        foundations.firstOrNull { s -> s.accepts(cards.first()) }
                            ?.let { foundation ->
                                val card = cards.first()

                                moveCardToFront(card)
                                foundation.place(card)

                                depot.flipTopCard()

                                spawnParticles(foundation)

                                moves++

                                return
                            }
                    }

                    depots
                        .filter { it !== depot }
                        .firstOrNull { it.accepts(cards.first()) }
                        ?.let { target ->
                            moveCardsToFront(cards)
                            target.placeAll(cards)

                            depot.flipTopCard()

                            moves++
                        }
                        ?: depot.placeAll(cards)
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
            "Time: %02d:%02d".format(gameTime.toInt() / 60, gameTime.toInt() % 60),
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

        if (state == State.WIN) {
            renderer.color = Color(0, 0, 0, 200)
            renderer.fillRect(0, 0, view.width, view.height)
            renderer.color = Color.WHITE
            renderer.font = Font("Monospaced", Font.BOLD, 100)
            renderer.drawString("You win!", view.bounds)
        }
        else if (state == State.LOSS) {
            renderer.color = Color(0, 0, 0, 200)
            renderer.fillRect(0, 0, view.width, view.height)
            renderer.color = Color.WHITE
            renderer.font = Font("Monospaced", Font.BOLD, 100)
            renderer.drawString("You lose...", view.bounds)
        }
    }

    override fun halt(view: View) = Unit
}