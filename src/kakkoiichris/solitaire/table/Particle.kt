package kakkoiichris.solitaire.table

import kakkoiichris.hypergame.Game
import kakkoiichris.hypergame.input.Input
import kakkoiichris.hypergame.media.Renderable
import kakkoiichris.hypergame.media.Renderer
import kakkoiichris.hypergame.util.Time
import kakkoiichris.hypergame.util.math.Vector
import kakkoiichris.hypergame.view.View
import java.awt.AlphaComposite
import kotlin.math.PI
import kotlin.random.Random

private const val PARTICLE_SIZE = 50

fun particles() =
    mutableListOf<Particle>()

class Particle(
    private var position: Vector,
    private val suit: Card.Suit,
) : Renderable {
    private var velocity = Vector.random() * Random.nextDouble(5.0)

    private var rotation = Random.nextDouble(PI * 2)

    private var alpha = 1F

    val isDead get() = alpha <= 0

    override fun update(view: View, game: Game, time: Time, input: Input) {
        position += velocity * time.delta

        velocity *= 0.975 * time.delta

        rotation += velocity.magnitude * 0.125

        alpha -= 0.015F * time.delta.toFloat()
    }

    override fun render(view: View, game: Game, renderer: Renderer) {
        renderer.push()

        renderer.translate(position)

        renderer.rotate(rotation)

        renderer.translate(-PARTICLE_SIZE / 2, -PARTICLE_SIZE / 2)

        renderer.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)

        renderer.drawImage(suit.sprite, 0, 0, PARTICLE_SIZE, PARTICLE_SIZE)

        renderer.pop()
    }
}