package kakkoiichris.solitaire.table

import kakkoiichris.hypergame.input.Input
import kakkoiichris.hypergame.media.Renderable
import kakkoiichris.hypergame.media.Renderer
import kakkoiichris.hypergame.media.Sprite
import kakkoiichris.hypergame.state.StateManager
import kakkoiichris.hypergame.util.Time
import kakkoiichris.hypergame.util.math.Box
import kakkoiichris.hypergame.util.math.Vector
import kakkoiichris.hypergame.view.View
import kakkoiichris.solitaire.Game
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D

sealed class CardSpace(x: Double, y: Double, width: Double, height: Double) : Box(x, y, width, height), Renderable {
    protected var highlight = false
    protected val idle = Sprite(width.toInt() + 10, height.toInt() + 10)
    protected val highlighted = Sprite(width.toInt() + 10, height.toInt() + 10)
    
    val cards = cards()
    
    val count get() = cards.size
    
    val indices get() = cards.indices
    
    init {
        idle.pixels.fill(0)
        
        var g = idle.createGraphics()
        
        g.color = Color(0, 0, 0, 128)
        g.stroke = BasicStroke(5F)
        g.drawRoundRect(5, 5, width.toInt(), height.toInt(), 10, 10)
        
        highlighted.pixels.fill(0)
        
        g = highlighted.createGraphics()
        
        g.color = Color(255, 255, 255, 50)
        g.fillRoundRect(5, 5, width.toInt(), height.toInt(), 10, 10)
        
        g.color = Color(0, 0, 0, 128)
        g.stroke = BasicStroke(5F)
        g.drawRoundRect(5, 5, width.toInt(), height.toInt(), 10, 10)
    }
    
    abstract fun accepts(card: Card): Boolean
    
    fun put(card: Card) {
        card.position = position
        
        card.target = position
        
        cards.add(card)
    }
    
    fun putAll(cards: Cards) {
        cards.forEach(this::put)
    }
    
    fun place(card: Card?) {
        if (card == null) return

        card.target = position

        card.pickedUp = false

        cards.add(card)
    }
    
    fun placeAll(cards: Cards) {
        cards.forEach(this::place)
    }
    
    fun take(up: Boolean): Card? {
        if (cards.isEmpty()) {
            return null
        }

        val card = cards.removeAt(cards.lastIndex)

        if (up) {
            card.pickedUp = true
        }

        return card
    }
    
    fun take(vector: Vector): List<Card> {
        val taken = mutableListOf<Card>()
        
        for (i in cards.indices.reversed()) {
            if (vector !in cards[i]) continue

            for (j in i until cards.size) {
                val card = cards.removeAt(i)

                card.pickedUp = true

                taken.add(card)
            }

            break
        }
        
        return taken
    }
    
    operator fun get(vector: Vector): Card? {
        for (i in cards.indices.reversed()) {
            if (vector in cards[i]) {
                return cards[i]
            }
        }
        
        return null
    }
    
    fun flipTopCard() {
        if (cards.isNotEmpty()) {
            cards.last().flipUp()
        }
    }
    
    fun shuffle() =
        cards.shuffle()
    
    fun isNotEmpty() =
        cards.isNotEmpty()
    
    fun removeAt(index: Int) =
        cards.removeAt(index)
    
    fun last() =
        cards.last()
    
    override fun update(view: View, manager: StateManager, time: Time, input: Input) {
        highlight = input.mouse in this

        if (cards.isEmpty()) return

        var i = 0

        do {
            cards[i].update(view, manager, time, input)
        }
        while (++i < cards.size)
    }
    
    override fun render(view: View, renderer: Renderer) {
        val sprite = if (highlight) highlighted else idle
        
        renderer.drawImage(sprite, x.toInt() - 5, y.toInt() - 5)

        if (cards.isEmpty()) return

        var i = 0

        do {
            cards[i].render(view, renderer)
        }
        while (++i < cards.size)
    }
    
    class Ace(x: Double, y: Double, width: Double, height: Double, val suit: Card.Suit) :
        CardSpace(x, y, width, height) {
        init {
            var g = idle.createGraphics() as Graphics2D
            
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5F)
            g.drawImage(
                suit.sprite, (5 + width / 2 - suit.sprite.width / 2).toInt(), (5 + height / 2 -
                    suit.sprite.width / 2).toInt(), null
            )
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1F)
            
            g.dispose()
            
            g = highlighted.createGraphics() as Graphics2D
            
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5F)
            g.drawImage(
                suit.sprite, (5 + width / 2 - suit.sprite.width / 2).toInt(), (5 + height / 2 -
                    suit.sprite.width / 2).toInt(), null
            )
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
            
            g.dispose()
        }
        
        override fun accepts(card: Card): Boolean {
            if (card.suit != suit) {
                return false
            }

            return if (cards.isEmpty()) {
                card.rank == Card.Rank.Ace
            }
            else {
                cards.last().rank.ordinal == card.rank.ordinal - 1
            }
        }
    }
    
    class Deck(x: Double, y: Double, width: Double, height: Double) : CardSpace(x, y, width, height) {
        companion object {
            private val icon = Game.resources.getFolder("img").getSprite("deck")
        }
        
        override fun accepts(card: Card) = false
        
        override fun render(view: View, renderer: Renderer) {
            super.render(view, renderer)

            if (highlight) return

            renderer.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5F)

            renderer.drawImage(
                icon,
                (x + 5).toInt(),
                (y + 5 + (height - width) / 2).toInt(),
                width.toInt() - 10,
                width.toInt() - 10,
                null
            )

            renderer.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1F)
        }
    }
    
    class Hand(x: Double, y: Double, width: Double, height: Double) : CardSpace(x, y, width, height) {
        companion object {
            private const val OFFSET = 40
            
            private val icon = Game.resources.getFolder("img").getSprite("hand")
        }
        
        init {
            var graphics = idle.graphics as Graphics2D
            
            graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5F)
            
            graphics.drawImage(
                icon,
                (x + 5).toInt(),
                (y + 5 + (height - width) / 2).toInt(),
                width.toInt() - 10,
                width.toInt() - 10,
                null
            )
            
            graphics = highlighted.graphics as Graphics2D
            
            graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5F)
            
            graphics.drawImage(
                icon,
                (x + 5).toInt(),
                (y + 5 + (height - width) / 2).toInt(),
                width.toInt() - 10,
                width.toInt() - 10,
                null
            )
        }
        
        fun inAll(vector: Vector): Boolean {
            for (card in cards.reversed()) {
                if (vector in card) {
                    return true
                }
            }
            
            return vector in this
        }
        
        override fun accepts(card: Card) = false
        
        override fun update(view: View, manager: StateManager, time: Time, input: Input) {
            super.update(view, manager, time, input)

            if (cards.isEmpty()) return

            cards.forEach { it.target = position.copy() }

            when (cards.size) {
                1    -> Unit

                2    -> cards[cards.lastIndex].target = position.copy(x = position.x + OFFSET)

                else -> {
                    cards[cards.lastIndex].target = position.copy(x = position.x + (OFFSET * 2))

                    cards[cards.lastIndex - 1].target = position.copy(x = position.x + OFFSET)
                }
            }
        }
    }
    
    class Stack(x: Double, y: Double, width: Double, height: Double) : CardSpace(x, y, width, height) {
        companion object {
            const val MIN_OFFSET = 10.0
            const val MAX_OFFSET = 35.0
        }
        
        private var hover = false

        private var hoverTimer = 0.0
        
        override fun accepts(card: Card) =
            if (cards.isEmpty()) {
                card.rank == Card.Rank.King
            }
            else {
                card.suit.isBlack != cards.last().suit.isBlack && card.rank.ordinal == cards.last().rank.ordinal - 1
            }
        
        fun inAll(vector: Vector): Boolean {
            for (card in cards.reversed()) {
                if (vector in card) {
                    return true
                }
            }
            
            return vector in this
        }
        
        override fun update(view: View, manager: StateManager, time: Time, input: Input) {
            super.update(view, manager, time, input)

            hover = inAll(input.mouse)

            if (hover) {
                for (i in cards.indices.reversed()) {
                    if (input.mouse !in cards[i]) continue

                    for (j in 0 until cards.size) {
                        cards[j].highlight = j >= i
                    }

                    break
                }
            }
            
            var offset = 0.0
            
            cards.forEach { card ->
                card.target = position.copy(y = position.y + offset)
                
                offset += when {
                    !card.faceUp -> MIN_OFFSET
                    
                    hover        -> MAX_OFFSET
                    
                    else         -> MIN_OFFSET
                }
            }
        }
    }
}
