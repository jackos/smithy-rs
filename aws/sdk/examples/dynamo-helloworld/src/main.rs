/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use std::error::Error;

use dynamodb::model::{
    AttributeDefinition, KeySchemaElement, KeyType, ProvisionedThroughput, ScalarAttributeType,
};

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let client = dynamodb::fluent::Client::from_env();
    let tables = client.list_tables().send().await?;

    println!("Current DynamoDB tables: {:?}", tables);

    let new_table = client
        .create_table()
        .table_name("test-table")
        .key_schema(vec![KeySchemaElement::builder()
            .attribute_name("k")
            .key_type(KeyType::Hash)
            .build()])
        .attribute_definitions(vec![AttributeDefinition::builder()
            .attribute_name("k")
            .attribute_type(ScalarAttributeType::S)
            .build()])
        .provisioned_throughput(
            ProvisionedThroughput::builder()
                .write_capacity_units(10)
                .read_capacity_units(10)
                .build(),
        )
        .send()
        .await?;
    println!(
        "new table: {:#?}",
        &new_table.table_description.unwrap().table_arn.unwrap()
    );
    Ok(())
}