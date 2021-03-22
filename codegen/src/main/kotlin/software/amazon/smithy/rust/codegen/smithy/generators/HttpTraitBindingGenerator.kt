/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.smithy.generators

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.pattern.SmithyPattern
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.MediaTypeTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.rust.codegen.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.rustlang.rust
import software.amazon.smithy.rust.codegen.rustlang.rustBlock
import software.amazon.smithy.rust.codegen.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.smithy.RuntimeType
import software.amazon.smithy.rust.codegen.util.dq
import software.amazon.smithy.rust.codegen.util.expectMember

fun HttpTrait.uriFormatString(): String {
    val base = uri.segments.joinToString("/", prefix = "/") {
        when {
            it.isLabel -> "{${it.content}}"
            else -> it.content
        }
    }
    // TODO: support query literals
    return base.dq()
}

/**
 * HttpTraitBindingGenerator
 *
 * Generates methods to serialize and deserialize requests/responses based on the HTTP trait. Specifically:
 * 1. `fn update_http_request(builder: http::request::Builder) -> Builder`
 *
 * This method takes a builder (perhaps pre configured with some headers) from the caller and sets the HTTP
 * headers & URL based on the HTTP trait implementation.
 *
 * More work is required to implement the entirety of https://awslabs.github.io/smithy/1.0/spec/core/http-traits.html
 * Specifically:
 * TODO: httpPrefixHeaders; 4h
 * TODO: Deserialization of all fields; 1w
 */
class HttpTraitBindingGenerator(
    val model: Model,
    private val symbolProvider: SymbolProvider,
    private val runtimeConfig: RuntimeConfig,
    private val writer: RustWriter,
    private val shape: OperationShape,
    private val inputShape: StructureShape,
    private val httpTrait: HttpTrait
) {
    // TODO: make defaultTimestampFormat configurable
    private val defaultTimestampFormat = TimestampFormatTrait.Format.EPOCH_SECONDS
    private val index = HttpBindingIndex.of(model)

    /**
     * Generates `update_http_builder` and all necessary dependency functions into the impl block provided by
     * [implBlockWriter]. The specific behavior is configured by [httpTrait].
     */
    fun renderUpdateHttpBuilder(implBlockWriter: RustWriter) {
        uriBase(implBlockWriter)
        val hasHeaders = addHeaders(implBlockWriter)
        val hasQuery = uriQuery(implBlockWriter)
        implBlockWriter.rustBlock(
            "fn update_http_builder(&self, builder: #1T) -> #1T",
            RuntimeType.HttpRequestBuilder
        ) {
            write("let mut uri = String::new();")
            write("self.uri_base(&mut uri);")
            if (hasQuery) {
                write("self.uri_query(&mut uri);")
            }
            if (hasHeaders) {
                write("let builder = self.add_headers(builder);")
            }
            write("builder.method(${httpTrait.method.dq()}).uri(uri)")
        }
    }

    /** Header Generation **/

    /**
     * If the protocol sets headers, generate a function to add headers to a request.
     * Returns `true` if headers were generated and false if are not required.
     */
    private fun addHeaders(writer: RustWriter): Boolean {
        val headers = index.getRequestBindings(shape, HttpBinding.Location.HEADER)
        if (headers.isEmpty()) {
            return false
        }
        writer.rustBlock(
            "fn add_headers(&self, mut builder: #1T) -> #1T",
            RuntimeType.HttpRequestBuilder
        ) {
            headers.forEach { httpBinding ->
                val memberShape = httpBinding.member
                val memberType = model.expectShape(memberShape.target)
                val memberSymbol = symbolProvider.toSymbol(memberShape)
                val memberName = symbolProvider.toMemberName(memberShape)
                ifSet(memberType, memberSymbol, "&self.$memberName") { field ->
                    ListForEach(memberType, field) { innerField, targetId ->
                        val innerMemberType = model.expectShape(targetId)
                        val formatted = headerFmtFun(innerMemberType, memberShape, innerField)
                        val safeName = safeName("formatted")
                        write("let $safeName = $formatted;")
                        rustBlock("if !$safeName.is_empty()") {
                            write("builder = builder.header(${httpBinding.locationName.dq()}, $formatted);")
                        }
                    }
                }
            }
            write("builder")
        }
        return true
    }

    /**
     * Format [member] in the when used as an HTTP header
     */
    private fun headerFmtFun(target: Shape, member: MemberShape, targetName: String): String {
        return when {
            target.isStringShape -> {
                val func = if (target.hasTrait(MediaTypeTrait::class.java)) {
                    writer.format(RuntimeType.Base64Encode(runtimeConfig))
                } else {
                    writer.format(RuntimeType.QueryFormat(runtimeConfig, "fmt_string"))
                }
                "$func(&${writer.useAs(target, targetName)})"
            }
            target.isTimestampShape -> {
                val timestampFormat =
                    index.determineTimestampFormat(member, HttpBinding.Location.HEADER, defaultTimestampFormat)
                val timestampFormatType = RuntimeType.TimestampFormat(runtimeConfig, timestampFormat)
                "$targetName.fmt(${writer.format(timestampFormatType)})"
            }
            target.isListShape || target.isMemberShape -> {
                throw IllegalArgumentException("lists should be handled at a higher level")
            }
            else -> {
                val func = writer.format(RuntimeType.QueryFormat(runtimeConfig, "fmt_default"))
                "$func(&$targetName)"
            }
        }
    }

    /** URI Generation **/

    /**
     * Generate a function to build the request URI
     */
    private fun uriBase(writer: RustWriter) {
        val formatString = httpTrait.uriFormatString()
        val args = httpTrait.uri.labels.map { label ->
            val member = inputShape.expectMember(label.content)
            "${label.content} = ${labelFmtFun(model.expectShape(member.target), member, label)}"
        }
        val combinedArgs = listOf(formatString, *args.toTypedArray())
        writer.addImport(RuntimeType.stdfmt.member("Write").toSymbol(), null)
        writer.rustBlock("fn uri_base(&self, output: &mut String)") {
            write("write!(output, ${combinedArgs.joinToString(", ")}).expect(\"formatting should succeed\")")
        }
    }

    /**
     * When needed, generate a function to build a query string
     *
     * This function uses smithy_http::query::Query to append params to a query string:
     * ```rust
     *    fn uri_query(&self, mut output: &mut String) {
     *      let mut query = smithy_http::query::Query::new(&mut output);
     *      if let Some(inner_89) = &self.null_value {
     *          query.push_kv("Null", &smithy_http::query::fmt_string(&inner_89));
     *      }
     *      if let Some(inner_90) = &self.empty_string {
     *          query.push_kv("Empty", &smithy_http::query::fmt_string(&inner_90));
     *      }
     *    }
     *  ```
     */
    private fun uriQuery(writer: RustWriter): Boolean {
        // Don't bother generating the function if we aren't going to make a query string
        val dynamicParams = index.getRequestBindings(shape, HttpBinding.Location.QUERY)
        val literalParams = httpTrait.uri.queryLiterals
        if (dynamicParams.isEmpty() && literalParams.isEmpty()) {
            return false
        }
        writer.rustBlock("fn uri_query(&self, mut output: &mut String)") {
            write("let mut query = #T::new(&mut output);", RuntimeType.QueryFormat(runtimeConfig, "Writer"))
            literalParams.forEach { (k, v) ->
                // When `v` is an empty string, no value should be set.
                // this generates a query string like `?k=v&xyz`
                if (v.isEmpty()) {
                    rust("query.push_v(${k.dq()});")
                } else {
                    rust("query.push_kv(${k.dq()}, ${v.dq()});")
                }
            }

            dynamicParams.forEach { param ->
                val memberShape = param.member
                val memberSymbol = symbolProvider.toSymbol(memberShape)
                val memberName = symbolProvider.toMemberName(memberShape)
                val outerTarget = model.expectShape(memberShape.target)
                ifSet(outerTarget, memberSymbol, "&self.$memberName") { field ->
                    ListForEach(outerTarget, field) { innerField, targetId ->
                        val target = model.expectShape(targetId)
                        rust(
                            "query.push_kv(${param.locationName.dq()}, &${
                            paramFmtFun(
                                target,
                                memberShape,
                                innerField
                            )
                            });"
                        )
                    }
                }
            }
        }
        return true
    }

    /**
     * Format [member] when used as a queryParam
     */
    private fun paramFmtFun(target: Shape, member: MemberShape, targetName: String): String {
        return when {
            target.isStringShape -> {
                val func = writer.format(RuntimeType.QueryFormat(runtimeConfig, "fmt_string"))
                "$func(&${writer.useAs(target, targetName)})"
            }
            target.isTimestampShape -> {
                val timestampFormat =
                    index.determineTimestampFormat(member, HttpBinding.Location.QUERY, defaultTimestampFormat)
                val timestampFormatType = RuntimeType.TimestampFormat(runtimeConfig, timestampFormat)
                val func = writer.format(RuntimeType.QueryFormat(runtimeConfig, "fmt_timestamp"))
                "$func($targetName, ${writer.format(timestampFormatType)})"
            }
            target.isListShape || target.isMemberShape -> {
                throw IllegalArgumentException("lists should be handled at a higher level")
            }
            else -> {
                val func = writer.format(RuntimeType.QueryFormat(runtimeConfig, "fmt_default"))
                "$func(&$targetName)"
            }
        }
    }

    /**
     * Format [member] when used as an HTTP Label (`/bucket/{key}`)
     */
    private fun labelFmtFun(target: Shape, member: MemberShape, label: SmithyPattern.Segment): String {
        val memberName = symbolProvider.toMemberName(member)
        return when {
            target.isStringShape -> {
                val func = writer.format(RuntimeType.LabelFormat(runtimeConfig, "fmt_string"))
                "$func(&self.$memberName, ${label.isGreedyLabel})"
            }
            target.isTimestampShape -> {
                val timestampFormat =
                    index.determineTimestampFormat(member, HttpBinding.Location.LABEL, defaultTimestampFormat)
                val timestampFormatType = RuntimeType.TimestampFormat(runtimeConfig, timestampFormat)
                val func = writer.format(RuntimeType.LabelFormat(runtimeConfig, "fmt_timestamp"))
                "$func(&self.$memberName, ${writer.format(timestampFormatType)})"
            }
            else -> {
                val func = writer.format(RuntimeType.LabelFormat(runtimeConfig, "fmt_default"))
                "$func(&self.$memberName)"
            }
        }
    }

    /** End URI generation **/
}