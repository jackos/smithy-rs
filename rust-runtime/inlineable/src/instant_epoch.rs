/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use serde::{Deserialize, Deserializer, Serialize, Serializer};
use smithy_types::Instant;

pub struct InstantEpoch(pub Instant);

impl Serialize for InstantEpoch {
    fn serialize<S>(&self, serializer: S) -> Result<<S as Serializer>::Ok, <S as Serializer>::Error>
    where
        S: Serializer,
    {
        if self.0.has_nanos() {
            serializer.serialize_f64(self.0.epoch_fractional_seconds())
        } else {
            serializer.serialize_i64(self.0.epoch_seconds())
        }
    }
}

impl<'de> Deserialize<'de> for InstantEpoch {
    fn deserialize<D>(deserializer: D) -> Result<Self, <D as Deserializer<'de>>::Error>
    where
        D: Deserializer<'de>,
    {
        let ts = f64::deserialize(deserializer)?;
        Ok(InstantEpoch(Instant::from_f64(ts)))
    }
}