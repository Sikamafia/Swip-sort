use std::sync::{Mutex, OnceLock};

#[derive(Clone, Copy)]
struct Decision {
    index: u64,
    action: u64,
}

static HISTORY: OnceLock<Mutex<Vec<Decision>>> = OnceLock::new();

fn history() -> &'static Mutex<Vec<Decision>> {
    HISTORY.get_or_init(|| Mutex::new(Vec::new()))
}

// JNI functions use the stable JNI symbol naming convention and primitive types.
// Android's JNI ABI passes JNIEnv and class/object pointers before method arguments.
#[no_mangle]
pub extern "system" fn Java_com_example_swipesort_RustCore_push(
    _env: *mut core::ffi::c_void,
    _class: *mut core::ffi::c_void,
    index: i64,
    action: i32,
) {
    if let Ok(mut h) = history().lock() {
        h.push(Decision { index: index.max(0) as u64, action: (action as u64) & 3 });
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_swipesort_RustCore_undo(
    _env: *mut core::ffi::c_void,
    _class: *mut core::ffi::c_void,
) -> i64 {
    match history().lock() {
        Ok(mut h) => match h.pop() {
            Some(d) => ((d.index << 2) | d.action) as i64,
            None => -1,
        },
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_swipesort_RustCore_clear(
    _env: *mut core::ffi::c_void,
    _class: *mut core::ffi::c_void,
) {
    if let Ok(mut h) = history().lock() { h.clear(); }
}

#[no_mangle]
pub extern "system" fn Java_com_example_swipesort_RustCore_count(
    _env: *mut core::ffi::c_void,
    _class: *mut core::ffi::c_void,
) -> i32 {
    history().lock().map(|h| h.len() as i32).unwrap_or(0)
}

#[cfg(test)]
mod tests {
    #[test]
    fn decision_round_trip_format() {
        let index = 17_u64;
        let action = 2_u64;
        let packed = (index << 2) | action;
        assert_eq!(packed >> 2, index);
        assert_eq!(packed & 3, action);
    }
}
