/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

use aws_types::os_shim_internal;

#[derive(Debug, Copy, Clone, Eq, PartialEq)]
pub(crate) enum Os {
    Windows,
    NotWindows,
}

impl Os {
    pub fn real() -> Self {
        match std::env::consts::OS {
            "windows" => Os::Windows,
            _ => Os::NotWindows,
        }
    }
}

/// Resolve a home directory given a set of environment variables
pub(crate) fn home_dir(env_var: &os_shim_internal::Env, os: Os) -> Option<String> {
    if let Ok(home) = env_var.get("HOME") {
        tracing::debug!(src = "HOME", "loaded home directory");
        return Some(home);
    }

    if os == Os::Windows {
        if let Ok(home) = env_var.get("USERPROFILE") {
            tracing::debug!(src = "USERPROFILE", "loaded home directory");
            return Some(home);
        }

        let home_drive = env_var.get("HOMEDRIVE");
        let home_path = env_var.get("HOMEPATH");
        tracing::debug!(src = "HOMEDRIVE/HOMEPATH", "loaded home directory");
        if let (Ok(mut drive), Ok(path)) = (home_drive, home_path) {
            drive.push_str(&path);
            return Some(drive);
        }
    }
    None
}

#[cfg(test)]
mod test {
    use super::*;
    use aws_types::os_shim_internal::Env;

    #[test]
    fn homedir_profile_only_windows() {
        // windows specific variables should only be considered when the platform is windows
        let env = Env::from_slice(&[("USERPROFILE", "C:\\Users\\name")]);
        assert_eq!(
            home_dir(&env, Os::Windows),
            Some("C:\\Users\\name".to_string())
        );
        assert_eq!(home_dir(&env, Os::NotWindows), None);
    }
}
