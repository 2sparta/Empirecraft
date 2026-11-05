package com.quintonc.vs_sails.ship;

/** Implemented by the Entity mixin, so finite water can update Forge's real immersion records. */
public interface FloodEntityAccess {
    void empirecraft$waterHeight(double height);
    void empirecraft$waterEyes(boolean wet);
}
