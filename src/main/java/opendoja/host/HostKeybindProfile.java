package opendoja.host;

import java.util.*;

public final class HostKeybindProfile {
    public static final int BINDING_SLOT_COUNT = 2;

    private final EnumMap<HostControlAction, HostInputBinding[]> bindingSlotsByAction;

    private HostKeybindProfile(EnumMap<HostControlAction, HostInputBinding[]> bindingSlotsByAction) {
        this.bindingSlotsByAction = normalize(bindingSlotsByAction);
    }

    public static HostKeybindProfile defaults() {
        EnumMap<HostControlAction, HostInputBinding[]> defaults = new EnumMap<>(HostControlAction.class);
        for (HostControlAction action : HostControlAction.values()) {
            HostInputBinding[] slots = new HostInputBinding[BINDING_SLOT_COUNT];
            List<HostInputBinding> defaultBindings = action.defaultBindings();
            for (int i = 0; i < defaultBindings.size() && i < BINDING_SLOT_COUNT; i++) {
                slots[i] = defaultBindings.get(i);
            }
            defaults.put(action, slots);
        }
        return new HostKeybindProfile(defaults);
    }

    public static HostKeybindProfile fromLaunchArgs() {
        return deserializeOrDefault(OpenDoJaLaunchArgs.get(OpenDoJaLaunchArgs.INPUT_BINDINGS, ""), defaults());
    }

    public static HostKeybindProfile deserialize(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return null;
        }
        EnumMap<HostControlAction, HostInputBinding[]> bindings = new EnumMap<>(HostControlAction.class);
        int parsedBindingCount = 0;
        for (String assignment : serialized.split(";")) {
            if (assignment == null || assignment.isBlank()) {
                continue;
            }
            int separatorIndex = assignment.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex >= assignment.length() - 1) {
                continue;
            }
            HostControlAction action = HostControlAction.fromId(assignment.substring(0, separatorIndex));
            if (action == null) {
                continue;
            }
            HostInputBinding[] slots = bindings.computeIfAbsent(action, key -> new HostInputBinding[BINDING_SLOT_COUNT]);
            String[] serializedBindings = assignment.substring(separatorIndex + 1).split(",", -1);
            for (int slotIndex = 0; slotIndex < serializedBindings.length && slotIndex < BINDING_SLOT_COUNT; slotIndex++) {
                String serializedBinding = serializedBindings[slotIndex];
                if (serializedBinding == null || serializedBinding.isBlank()) {
                    continue;
                }
                HostInputBinding binding = HostInputBinding.parse(serializedBinding);
                if (binding == null) {
                    continue;
                }
                slots[slotIndex] = binding;
                parsedBindingCount++;
            }
        }
        return parsedBindingCount == 0 ? null : new HostKeybindProfile(bindings);
    }

    public static HostKeybindProfile deserializeOrDefault(String serialized, HostKeybindProfile fallback) {
        HostKeybindProfile parsed = deserialize(serialized);
        return parsed == null ? fallback : parsed;
    }

    public HostInputBinding bindingAt(HostControlAction action, int slotIndex) {
        validateSlotIndex(slotIndex);
        HostInputBinding[] slots = bindingSlotsByAction.get(action);
        return slots == null ? null : slots[slotIndex];
    }

    public List<HostInputBinding> bindingsFor(HostControlAction action) {
        HostInputBinding[] slots = bindingSlotsByAction.get(action);
        if (slots == null) {
            return List.of();
        }
        ArrayList<HostInputBinding> bindings = new ArrayList<>(BINDING_SLOT_COUNT);
        for (HostInputBinding slot : slots) {
            if (slot != null) {
                bindings.add(slot);
            }
        }
        return List.copyOf(bindings);
    }

    public HostKeybindProfile withBinding(HostControlAction action, int slotIndex, HostInputBinding binding) {
        Objects.requireNonNull(action, "action");
        validateSlotIndex(slotIndex);
        if (binding != null && binding.isKeyboard()) {
            Integer keyboardKeyCode = binding.keyboardKeyCode();
            if (keyboardKeyCode == null || !HostInputBinding.isBindableKeyboardKeyCode(keyboardKeyCode)) {
                throw new IllegalArgumentException("Unsupported keyboard binding: " + binding);
            }
        }
        EnumMap<HostControlAction, HostInputBinding[]> updated = copy(bindingSlotsByAction);
        if (binding != null) {
            for (HostInputBinding[] slots : updated.values()) {
                for (int i = 0; i < slots.length; i++) {
                    if (binding.equals(slots[i])) {
                        slots[i] = null;
                    }
                }
            }
        }
        updated.computeIfAbsent(action, key -> new HostInputBinding[BINDING_SLOT_COUNT])[slotIndex] = binding;
        return new HostKeybindProfile(updated);
    }

    public HostKeybindProfile withoutBinding(HostControlAction action, int slotIndex) {
        return withBinding(action, slotIndex, null);
    }

    public Map<Integer, HostControlAction> keyboardActionsByKeyCode() {
        HashMap<Integer, HostControlAction> bindings = new HashMap<>();
        for (HostControlAction action : HostControlAction.values()) {
            HostInputBinding[] slots = bindingSlotsByAction.get(action);
            if (slots == null) {
                continue;
            }
            for (HostInputBinding binding : slots) {
                if (binding == null || !binding.isKeyboard()) {
                    continue;
                }
                Integer keyCode = binding.keyboardKeyCode();
                if (keyCode != null) {
                    bindings.put(keyCode, action);
                }
            }
        }
        return Map.copyOf(bindings);
    }

    public Map<HostInputBinding, HostControlAction> controllerActionsByBinding() {
        HashMap<HostInputBinding, HostControlAction> bindings = new HashMap<>();
        for (HostControlAction action : HostControlAction.values()) {
            HostInputBinding[] slots = bindingSlotsByAction.get(action);
            if (slots == null) {
                continue;
            }
            for (HostInputBinding binding : slots) {
                if (binding != null && binding.isController()) {
                    bindings.put(binding, action);
                }
            }
        }
        return Map.copyOf(bindings);
    }

    public String serialize() {
        StringJoiner joiner = new StringJoiner(";");
        for (HostControlAction action : HostControlAction.values()) {
            HostInputBinding[] slots = bindingSlotsByAction.get(action);
            if (slots == null || slots[0] == null && slots[1] == null) {
                continue;
            }
            StringBuilder encoded = new StringBuilder(action.id()).append('=');
            for (int i = 0; i < BINDING_SLOT_COUNT; i++) {
                if (i > 0) {
                    encoded.append(',');
                }
                if (slots[i] != null) {
                    encoded.append(slots[i].serialize());
                }
            }
            joiner.add(encoded);
        }
        return joiner.toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof HostKeybindProfile that)) {
            return false;
        }
        return serialize().equals(that.serialize());
    }

    @Override
    public int hashCode() {
        return serialize().hashCode();
    }

    @Override
    public String toString() {
        return serialize();
    }

    private static EnumMap<HostControlAction, HostInputBinding[]> normalize(Map<HostControlAction, HostInputBinding[]> bindings) {
        EnumMap<HostControlAction, HostInputBinding[]> normalized = new EnumMap<>(HostControlAction.class);
        Set<HostInputBinding> usedBindings = new HashSet<>();
        for (HostControlAction action : HostControlAction.values()) {
            HostInputBinding[] sourceSlots = bindings == null ? null : bindings.get(action);
            HostInputBinding[] targetSlots = new HostInputBinding[BINDING_SLOT_COUNT];
            if (sourceSlots != null) {
                for (int i = 0; i < sourceSlots.length && i < BINDING_SLOT_COUNT; i++) {
                    HostInputBinding binding = sourceSlots[i];
                    if (binding == null || !usedBindings.add(binding)) {
                        continue;
                    }
                    targetSlots[i] = binding;
                }
            }
            normalized.put(action, targetSlots);
        }
        return normalized;
    }

    private static EnumMap<HostControlAction, HostInputBinding[]> copy(Map<HostControlAction, HostInputBinding[]> source) {
        EnumMap<HostControlAction, HostInputBinding[]> copy = new EnumMap<>(HostControlAction.class);
        for (HostControlAction action : HostControlAction.values()) {
            HostInputBinding[] slots = source == null ? null : source.get(action);
            copy.put(action, slots == null ? new HostInputBinding[BINDING_SLOT_COUNT] : slots.clone());
        }
        return copy;
    }

    private static void validateSlotIndex(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= BINDING_SLOT_COUNT) {
            throw new IllegalArgumentException("Unsupported binding slot: " + slotIndex);
        }
    }
}
