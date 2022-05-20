/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.NullableIndex
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.SimpleShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.RequiredTrait
import software.amazon.smithy.rust.codegen.rustlang.RustReservedWords
import software.amazon.smithy.rust.codegen.rustlang.RustType
import software.amazon.smithy.rust.codegen.util.PANIC
import software.amazon.smithy.rust.codegen.util.hasTrait
import software.amazon.smithy.rust.codegen.util.toPascalCase
import software.amazon.smithy.rust.codegen.util.toSnakeCase

// TODO Unit tests.
/**
 * The [PubCrateConstrainedShapeSymbolProvider] returns, for a given
 * _transitively but not directly_ constrained shape, a symbol whose Rust type
 * can hold the constrained values.
 *
 * For collection and map shapes, this type is a [RustType.Opaque] wrapper
 * tuple newtype holding a container over the inner constrained type. For
 * member shapes, it's whatever their target shape resolves to.
 *
 * The class name is prefixed with `PubCrate` because the symbols it returns
 * have associated types that are generated as `pub(crate)`. See the
 * `PubCrate*Generator` classes to see how these types are generated.
 *
 * It is important that this symbol provider _not_ wrap
 * [ConstrainedShapeSymbolProvider], since otherwise it will eventually
 * delegate to it and generate a symbol with a `pub` type.
 *
 * Note simple shapes cannot be transitively but not directly constrained, so
 * this symbol provider is only implemented for aggregate shapes. The symbol
 * provider will intentionally crash in such a case to avoid the caller
 * incorrectly using it.
 *
 * If the shape is _directly_ constrained, use [ConstrainedShapeSymbolProvider]
 * instead.
 */
class PubCrateConstrainedShapeSymbolProvider(
    private val base: RustSymbolProvider,
    private val model: Model,
    private val serviceShape: ServiceShape,
) : WrappingSymbolProvider(base) {
    private val nullableIndex = NullableIndex.of(model)

    private fun constrainedSymbolForCollectionOrMapShape(shape: Shape): Symbol {
        check(shape is CollectionShape || shape is MapShape)

        val name = constrainedTypeNameForCollectionOrMapShape(shape, serviceShape)
        val namespace = "crate::${Constrained.namespace}::${RustReservedWords.escapeIfNeeded(name.toSnakeCase())}"
        val rustType = RustType.Opaque(name, namespace)
        return Symbol.builder()
            .rustType(rustType)
            .name(rustType.name)
            .namespace(rustType.namespace, "::")
            .definitionFile(Constrained.filename)
            .build()
    }

    // TODO The following two methods have been copied from `SymbolVisitor.kt`.
    private fun handleOptionality(symbol: Symbol, member: MemberShape): Symbol =
        if (member.isRequired) {
            symbol
        } else if (nullableIndex.isNullable(member)) {
            symbol.makeOptional()
        } else {
            symbol
        }

    /**
     * Boxes and returns [symbol], the symbol for the target of the member shape [shape], if [shape] is annotated with
     * [RustBoxTrait]; otherwise returns [symbol] unchanged.
     *
     * See `RecursiveShapeBoxer.kt` for the model transformation pass that annotates model shapes with [RustBoxTrait].
     */
    private fun handleRustBoxing(symbol: Symbol, shape: MemberShape): Symbol =
        if (shape.hasTrait<RustBoxTrait>()) {
            symbol.makeRustBoxed()
        } else symbol

    private fun errorMessage(shape: Shape) =
        "This symbol provider was called with $shape. However, it can only be called with a shape that is transitively constrained"

    override fun toSymbol(shape: Shape): Symbol {
        require(shape.isTransitivelyConstrained(model, base)) { errorMessage(shape) }

        return when (shape) {
            is CollectionShape, is MapShape -> {
                constrainedSymbolForCollectionOrMapShape(shape)
            }
            is MemberShape -> {
                check(model.expectShape(shape.container).isStructureShape)

                if (shape.requiresNewtype()) {
                    //TODO()

                    // TODO What follows is wrong; here we should refer to an opaque type for the member shape.
                    //     But for now we add this to not make the validation model crash.

                    val targetShape = model.expectShape(shape.target)
                    if (targetShape is SimpleShape) {
                        base.toSymbol(shape)
                    } else {
                        val targetSymbol = this.toSymbol(targetShape)
                        // Handle boxing first so we end up with `Option<Box<_>>`, not `Box<Option<_>>`.
                        handleOptionality(handleRustBoxing(targetSymbol, shape), shape)
                    }
                } else {
                    val targetShape = model.expectShape(shape.target)

                    if (targetShape is SimpleShape) {
                        check(shape.hasTrait<RequiredTrait>()) {
                            "Targeting a simple shape that can reach a constrained shape and does not need a newtype; the member shape must be `required`"
                        }

                        base.toSymbol(shape)
                    } else {
                        val targetSymbol = this.toSymbol(targetShape)
                        // Handle boxing first so we end up with `Option<Box<_>>`, not `Box<Option<_>>`.
                        handleOptionality(handleRustBoxing(targetSymbol, shape), shape)
                    }
                }
            }
            is StructureShape, is UnionShape -> {
                // Structure shapes and union shapes always generate a [RustType.Opaque] constrained type.
                base.toSymbol(shape)
            }
            else -> {
                check(shape is SimpleShape)
                // The rest of the shape types are simple shapes; they generate a public constrained type.
                PANIC(errorMessage(shape))
            }
        }
    }
}

fun constrainedTypeNameForCollectionOrMapShape(shape: Shape, serviceShape: ServiceShape): String {
    check(shape is CollectionShape || shape is MapShape)
    return "${shape.id.getName(serviceShape).toPascalCase()}Constrained"
}
