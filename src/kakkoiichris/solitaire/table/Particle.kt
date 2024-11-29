package kakkoiichris.solitaire.table

import kakkoiichris.hypergame.input.Input
import kakkoiichris.hypergame.media.Renderable
import kakkoiichris.hypergame.media.Renderer
import kakkoiichris.hypergame.state.StateManager
import kakkoiichris.hypergame.util.Time
import kakkoiichris.hypergame.util.math.Vector
import kakkoiichris.hypergame.view.View
import java.awt.AlphaComposite
import kotlin.math.PI
import kotlin.random.Random

fun particles() =
    mutableListOf<Particle>()

class Particle(
    private var position: Vector,
    private val suit: Card.Suit,
) : Renderable {
    companion object {
        private const val SIZE = 50
    }
    
    private var velocity = Vector.random() * Random.nextDouble(5.0)
    
    private var rotation = Random.nextDouble(PI * 2)
    
    private var alpha = 1F
    
    val isDead get() = alpha <= 0
    
    override fun update(view: View, manager: StateManager, time: Time, input: Input) {
        position += velocity * time.delta
        
        velocity *= .975 * time.delta
        
        rotation += velocity.magnitude * 0.125
        
        alpha -= .015F * time.delta.toFloat()
    }
    
    override fun render(view: View, renderer: Renderer) {
        renderer.push()
        
        renderer.translate(position)
        
        renderer.rotate(rotation)
        
        renderer.translate(-SIZE / 2, -SIZE / 2)
        
        renderer.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        
        renderer.drawImage(suit.sprite, 0, 0, SIZE, SIZE)
        
        renderer.pop()
    }
}