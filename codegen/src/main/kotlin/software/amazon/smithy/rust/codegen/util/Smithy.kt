/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.util

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape

inline fun <reified T : Shape> Model.lookup(shapeId: String): T {
    return this.expectShape(ShapeId.from(shapeId), T::class.java)
}

fun OperationShape.inputShape(model: Model): StructureShape {
    // The Rust Smithy generator adds an input to all shapes automatically
    return model.expectShape(this.input.get(), StructureShape::class.java)
}

fun OperationShape.outputShape(model: Model): StructureShape {
    // The Rust Smithy generator adds an output to all shapes automatically
    return model.expectShape(this.output.get(), StructureShape::class.java)
}

fun StructureShape.expectMember(member: String): MemberShape =
    this.getMember(member).orElseThrow { CodegenException("$member did not exist on $this") }

fun UnionShape.expectMember(member: String): MemberShape =
    this.getMember(member).orElseThrow { CodegenException("$member did not exist on $this") }