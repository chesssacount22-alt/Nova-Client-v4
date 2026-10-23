package com.novaclient.module;

import com.novaclient.NovaConfig;

/** Convenient base for your own new modules: the on/off state is stored by Nova itself. */
public abstract class StoredModule extends Module {
    protected StoredModule(String name, String description, Category category) { super(name, description, category); }

    @Override public boolean isEnabled() { return NovaConfig.getBool("mod." + id() + ".enabled", false); }
    @Override public void setEnabled(boolean on) { NovaConfig.setBool("mod." + id() + ".enabled", on); onToggle(on); }
    protected void onToggle(boolean on) {}
}
