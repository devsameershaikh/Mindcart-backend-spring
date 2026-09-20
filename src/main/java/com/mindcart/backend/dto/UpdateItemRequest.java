package com.mindcart.backend.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Deliberately a loose bag of optional fields, mirroring the original
 * Express handler which whitelists a fixed set of updatable keys and only
 * applies the ones actually present in the body (partial update / PATCH
 * semantics). Kept as a raw map so "field not present" and "field present
 * but null" stay distinguishable, same as `req.body[k] !== undefined` did.
 */
public class UpdateItemRequest extends HashMap<String, Object> {
    public UpdateItemRequest() {
        super();
    }
    public UpdateItemRequest(Map<String, Object> source) {
        super(source);
    }
}
