// Code generated by software.amazon.smithy.rust.codegen.smithy-rs. DO NOT EDIT.
/// Service register input structure
#[non_exhaustive]
#[derive(std::clone::Clone, std::cmp::PartialEq)]
pub struct RegisterServiceInput {
    /// Id of the service that will be registered
    pub id: std::option::Option<std::string::String>,
    /// Name of the service that will be registered
    pub name: std::option::Option<std::string::String>,
}
impl RegisterServiceInput {
    /// Id of the service that will be registered
    pub fn id(&self) -> std::option::Option<&str> {
        self.id.as_deref()
    }
    /// Name of the service that will be registered
    pub fn name(&self) -> std::option::Option<&str> {
        self.name.as_deref()
    }
}
impl std::fmt::Debug for RegisterServiceInput {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let mut formatter = f.debug_struct("RegisterServiceInput");
        formatter.field("id", &self.id);
        formatter.field("name", &self.name);
        formatter.finish()
    }
}
/// See [`RegisterServiceInput`](crate::input::RegisterServiceInput)
pub mod register_service_input {
    /// A builder for [`RegisterServiceInput`](crate::input::RegisterServiceInput)
    #[non_exhaustive]
    #[derive(std::default::Default, std::clone::Clone, std::cmp::PartialEq, std::fmt::Debug)]
    pub struct Builder {
        pub(crate) id: std::option::Option<std::string::String>,
        pub(crate) name: std::option::Option<std::string::String>,
    }
    impl Builder {
        /// Id of the service that will be registered
        pub fn id(mut self, input: impl Into<std::string::String>) -> Self {
            self.id = Some(input.into());
            self
        }
        /// Id of the service that will be registered
        pub fn set_id(mut self, input: std::option::Option<std::string::String>) -> Self {
            self.id = input;
            self
        }
        /// Name of the service that will be registered
        pub fn name(mut self, input: impl Into<std::string::String>) -> Self {
            self.name = Some(input.into());
            self
        }
        /// Name of the service that will be registered
        pub fn set_name(mut self, input: std::option::Option<std::string::String>) -> Self {
            self.name = input;
            self
        }
        /// Consumes the builder and constructs a [`RegisterServiceInput`](crate::input::RegisterServiceInput)
        pub fn build(
            self,
        ) -> std::result::Result<
            crate::input::RegisterServiceInput,
            aws_smithy_http::operation::BuildError,
        > {
            Ok(crate::input::RegisterServiceInput {
                id: self.id,
                name: self.name,
            })
        }
    }
}
impl RegisterServiceInput {
    /// Creates a new builder-style object to manufacture [`RegisterServiceInput`](crate::input::RegisterServiceInput)
    pub fn builder() -> crate::input::register_service_input::Builder {
        crate::input::register_service_input::Builder::default()
    }
}

/// Service healthcheck output structure
#[non_exhaustive]
#[derive(std::clone::Clone, std::cmp::PartialEq)]
pub struct HealthcheckInput {}
impl std::fmt::Debug for HealthcheckInput {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let mut formatter = f.debug_struct("HealthcheckInput");
        formatter.finish()
    }
}
/// See [`HealthcheckInput`](crate::input::HealthcheckInput)
pub mod healthcheck_input {
    /// A builder for [`HealthcheckInput`](crate::input::HealthcheckInput)
    #[non_exhaustive]
    #[derive(std::default::Default, std::clone::Clone, std::cmp::PartialEq, std::fmt::Debug)]
    pub struct Builder {}
    impl Builder {
        /// Consumes the builder and constructs a [`HealthcheckInput`](crate::input::HealthcheckInput)
        pub fn build(
            self,
        ) -> std::result::Result<
            crate::input::HealthcheckInput,
            aws_smithy_http::operation::BuildError,
        > {
            Ok(crate::input::HealthcheckInput {})
        }
    }
}
impl HealthcheckInput {
    /// Creates a new builder-style object to manufacture [`HealthcheckInput`](crate::input::HealthcheckInput)
    pub fn builder() -> crate::input::healthcheck_input::Builder {
        crate::input::healthcheck_input::Builder::default()
    }
}