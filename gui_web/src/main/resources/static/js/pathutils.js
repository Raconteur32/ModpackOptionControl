// JSON path helpers matching common/.../DiffUtils.kt's isDescendant/directChildren
// semantics: paths look like "$.client.maxFps" or "$['weird key'][0]".

export function isDescendant(childPath, parentPath) {
    if (childPath.length <= parentPath.length) return false;
    if (!childPath.startsWith(parentPath)) return false;
    const c = childPath[parentPath.length];
    return c === '.' || c === '[';
}

export function isAncestor(parentPath, childPath) {
    return isDescendant(childPath, parentPath);
}
