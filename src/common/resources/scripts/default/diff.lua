local M = {}

local function sorted_keys(t)
    local keys = {}
    for k in pairs(t) do keys[#keys + 1] = k end
    table.sort(keys)
    return keys
end

local function is_array(val)
    return type(val) == "table" and #val > 0
end

-- Fills flat_diff (FlatDiffAPIWrapper) with the difference between from and to (MocFileAPIWrapper)
function M.diff(from, to, flat_diff)
    local from_flat = from:get_flat_content()
    local to_flat   = to:get_flat_content()

    local from_keys = sorted_keys(from_flat)
    local to_keys   = sorted_keys(to_flat)

    -- Deletions: from ascending
    for _, path in ipairs(from_keys) do
        if to_flat[path] == nil then
            flat_diff:add_deleted(path)
        end
    end

    -- New and changed: to descending (deepest first)
    for i = #to_keys, 1, -1 do
        local path   = to_keys[i]
        local to_val = to_flat[path]

        if from_flat[path] == nil then
            flat_diff:add_new(path)
            if is_array(to_val) then
                flat_diff:cut_branch(path)
            end
        else
            if type(to_val) == "table" then
                if flat_diff:has_leaf(path) then
                    flat_diff:add_changed(path)
                    if is_array(to_val) then
                        flat_diff:cut_branch(path)
                    end
                end
            else
                if from_flat[path] ~= to_val then
                    flat_diff:add_changed(path)
                end
            end
        end
    end

    flat_diff:rationalize()
end

return M
