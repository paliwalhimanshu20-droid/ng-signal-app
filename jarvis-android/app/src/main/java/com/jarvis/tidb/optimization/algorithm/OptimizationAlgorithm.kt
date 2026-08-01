package com.jarvis.tidb.optimization.algorithm

import com.jarvis.tidb.optimization.searchspace.ParameterSpec
import com.jarvis.tidb.optimization.searchspace.SearchSpace
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Optimization Algorithms" (Module 11). One implementation per search strategy over a
 * [SearchSpace] -- [GridSearchAlgorithm] and [RandomSearchAlgorithm] are the two required at
 * this phase; Bayesian Optimization, Genetic Algorithms, Particle Swarm Optimization, Simulated
 * Annealing, Hyperband, and Optuna-style samplers are all future implementers of this exact same
 * interface, added the same swap-point way as everywhere else in this codebase (implement, bind
 * `@IntoSet`) -- none of them need this interface, [GridSearchAlgorithm], or
 * [RandomSearchAlgorithm] to change.
 *
 * [budget]'s meaning is intentionally the same knob for every algorithm -- "how many combinations
 * this run is allowed/wants to produce" -- but each algorithm interprets it in the way that's
 * natural for its own strategy: Grid Search treats it as a hard cap it refuses to exceed (an
 * exhaustive sweep either fits or it doesn't -- silently truncating a grid would silently make it
 * non-exhaustive, defeating the point of choosing grid search at all); Random Search treats it as
 * exactly how many samples to draw, since sampling N is the entire algorithm.
 */
interface OptimizationAlgorithm {
    val algorithmId: String
    fun generateCombinations(searchSpace: SearchSpace, budget: Int, randomSeed: Long? = null): List<Map<String, Double>>
}

/**
 * Exhaustive cartesian product over every [ParameterSpec] in the space. Refuses (throws) rather
 * than silently truncating when the full grid would exceed [budget] -- see this file's own
 * interface doc for why that's the honest behavior for an "exhaustive" search specifically.
 */
@Singleton
class GridSearchAlgorithm @Inject constructor() : OptimizationAlgorithm {
    override val algorithmId = "GRID_SEARCH"

    override fun generateCombinations(searchSpace: SearchSpace, budget: Int, randomSeed: Long?): List<Map<String, Double>> {
        if (searchSpace.parameters.isEmpty()) return listOf(emptyMap())

        val valueLists: List<Pair<String, List<Double>>> = searchSpace.parameters.map { it.key to valuesFor(it) }
        val totalCombinations = valueLists.fold(1L) { acc, (_, values) -> acc * values.size }
        require(totalCombinations <= budget) {
            "Grid search over '${searchSpace.componentId}' would generate $totalCombinations combinations, " +
                "exceeding the budget of $budget -- narrow the ranges, or use RandomSearchAlgorithm instead."
        }

        var combinations = listOf(emptyMap<String, Double>())
        for ((key, values) in valueLists) {
            combinations = combinations.flatMap { partial -> values.map { value -> partial + (key to value) } }
        }
        return combinations
    }

    private fun valuesFor(spec: ParameterSpec): List<Double> = when (spec) {
        is ParameterSpec.ContinuousRange -> {
            require(spec.step > 0.0) { "ContinuousRange '${spec.key}' has a non-positive step (${spec.step})." }
            generateSequence(spec.min) { it + spec.step }.takeWhile { it <= spec.max + EPSILON }.toList()
        }
        is ParameterSpec.DiscreteChoices -> spec.choices
    }

    private companion object {
        /** Floating-point tolerance so a range like 1.0..4.0 step 0.5 reliably includes its own max (4.0) despite repeated addition drift. */
        const val EPSILON = 1e-9
    }
}

/**
 * Uniform random sampling: draws exactly [budget] combinations, each dimension sampled
 * independently. [randomSeed], when provided, makes a run fully reproducible -- the same seed
 * against the same [SearchSpace] always produces the same combinations, in the same order, which
 * matters for Module 12's future validation work (a walk-forward run needs to know it's
 * re-testing the exact same candidate configurations on a later window, not a fresh random set).
 */
@Singleton
class RandomSearchAlgorithm @Inject constructor() : OptimizationAlgorithm {
    override val algorithmId = "RANDOM_SEARCH"

    override fun generateCombinations(searchSpace: SearchSpace, budget: Int, randomSeed: Long?): List<Map<String, Double>> {
        require(budget > 0) { "Random search budget must be positive, was $budget." }
        if (searchSpace.parameters.isEmpty()) return listOf(emptyMap())

        val random = if (randomSeed != null) Random(randomSeed) else Random()
        return (0 until budget).map {
            searchSpace.parameters.associate { spec -> spec.key to sampleOne(spec, random) }
        }
    }

    private fun sampleOne(spec: ParameterSpec, random: Random): Double = when (spec) {
        is ParameterSpec.ContinuousRange -> {
            val steps = ((spec.max - spec.min) / spec.step).toInt()
            spec.min + random.nextInt(steps + 1) * spec.step
        }
        is ParameterSpec.DiscreteChoices -> spec.choices[random.nextInt(spec.choices.size)]
    }
}
