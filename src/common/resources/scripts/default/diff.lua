local M = {}

-- Retourne les clés d'une table triées alphabétiquement.
-- Le tri garantit un ordre déterministe : les chemins parents ("$.a")
-- arrivent toujours avant leurs enfants ("$.a.b") en ordre croissant,
-- et toujours après en ordre décroissant (utilisé pour le parcours des ajouts).
local function sorted_keys(t)
    local keys = {}
    for k in pairs(t) do keys[#keys + 1] = k end
    table.sort(keys)
    return keys
end

-- Détecte si une valeur est un tableau JSON.
-- Utilise le metatable __is_array posé par jsonToLua (côté Kotlin) ou moc.utils.get_empty_array().
-- Repli sur #val > 0 pour les tables sans metatable (compatibilité).
local function is_array(val)
    if type(val) ~= "table" then return false end
    local mt = getmetatable(val)
    if mt then return mt.__is_array == true end
    return #val > 0
end

-- Compare deux scalaires en tenant compte du type numérique (entier vs décimal).
-- Sans ce contrôle, LuaJ traite 1 == 1.0, masquant les changements int → float.
local function values_equal(a, b)
    if a ~= b then return false end
    if type(a) == "number" then
        return api.utils.is_integer(a) == api.utils.is_integer(b)
    end
    return true
end

-- Calcule le diff entre deux fichiers MOC.
-- `from` : état de référence (ancienne version)
-- `to`   : état courant (nouvelle version)
-- `flat_diff` : accumulateur de résultat (FlatContentDiff côté Kotlin)
function M.diff(from, to, flat_diff)
    -- Si `from` n'existe pas, on utilise une map vide taguée : chaque clé de `to`
    -- sera naturellement traitée comme un ajout par le reste de la logique,
    -- avec la granularité normale (une entrée par chemin, pas un seul bloc "$").
    local from_flat = from:get_flat_content() or api.utils.get_empty_map()
    local to_flat   = to:get_flat_content()

    -- Cas : `to` n'existe pas sur disque (fichier supprimé).
    -- Le chemin "" (chaîne vide) est la convention pour une suppression de fichier
    -- entier, distincte de "$" qui désigne le contenu racine d'un fichier existant.
    if to_flat == nil then
        flat_diff:add_deleted("", from_flat["$"])
        return
    end

    local from_keys = sorted_keys(from_flat)
    local to_keys   = sorted_keys(to_flat)

    -- Passe 1 — suppressions : parcours de `from` en ordre croissant.
    -- Un chemin présent dans `from` mais absent de `to` a été supprimé.
    -- L'ordre croissant n'est pas critique ici mais reste cohérent avec sorted_keys.
    for _, path in ipairs(from_keys) do
        if to_flat[path] == nil then
            flat_diff:add_deleted(path, from_flat[path])
        end
    end

    -- Passe 2 — ajouts et modifications : parcours de `to` en ordre décroissant
    -- (les enfants avant les parents).
    -- L'ordre décroissant est essentiel : il permet à cut_branch() d'éliminer
    -- les sous-chemins redondants avant que leur parent soit traité.
    for i = #to_keys, 1, -1 do
        local path   = to_keys[i]
        local to_val = to_flat[path]

        if from_flat[path] == nil then
            -- Chemin inexistant dans `from` : ajout.
            flat_diff:add_new(path, to_val)

            -- Si la valeur ajoutée est un tableau, ses éléments indexés ont déjà
            -- été enregistrés individuellement (chemins "$.a[0]", "$.a[1]"...).
            -- cut_branch() supprime ces entrées enfants redondantes : l'entrée
            -- parente "$" suffit à représenter l'ajout de tout le tableau.
            if is_array(to_val) then
                flat_diff:cut_branch(path)
            end
        else
            -- Chemin présent dans les deux versions : possible modification.

            if type(to_val) == "table" then
                -- La valeur est un objet ou tableau dans `to`.
                -- On ne compare pas les tables directement (références Lua distinctes).
                -- has_leaf() vérifie si des enfants de ce chemin ont déjà été
                -- enregistrés comme modifiés : si oui, le nœud parent a changé
                -- structurellement et mérite une entrée "changed" sans valeurs
                -- (nil, nil) pour signaler le changement au niveau du conteneur.
                if flat_diff:has_leaf(path) then
                    flat_diff:add_changed(path, from_flat[path], to_val)

                    -- Même logique que pour les ajouts : si la nouvelle valeur
                    -- est un tableau, les entrées enfants indexées sont redondantes.
                    if is_array(to_val) then
                        flat_diff:cut_branch(path)
                    end
                end
                -- Si aucun enfant n'a changé, le conteneur est identique : on ne
                -- fait rien (pas d'entrée dans le diff).
            else
                -- Valeur scalaire (string, number, boolean) : comparaison directe.
                -- values_equal distingue int et float (1 ~= 1.0) via is_integer.
                if not values_equal(from_flat[path], to_val) then
                    flat_diff:add_changed(path, from_flat[path], to_val)
                end
            end
        end
    end

    -- Rationalize() nettoie le diff final : supprime les entrées enfants dont
    -- le parent est lui-même marqué comme supprimé (redondance hiérarchique).
    flat_diff:rationalize()
end

return M
