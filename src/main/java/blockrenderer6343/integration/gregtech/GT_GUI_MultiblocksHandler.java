package blockrenderer6343.integration.gregtech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.StructureEvent;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ConstructableUtility;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructableProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.mojang.authlib.GameProfile;

import blockrenderer6343.BlockRenderer6343;
import blockrenderer6343.api.utils.BlockPosition;
import blockrenderer6343.api.utils.CreativeItemSource;
import blockrenderer6343.api.utils.PositionedIStructureElement;
import blockrenderer6343.client.world.ClientFakePlayer;
import blockrenderer6343.common.GUI_MultiblocksHandler;
import codechicken.lib.math.MathHelper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.ITurnable;
import gregtech.api.threads.GT_Runnable_MachineBlockUpdate;

public class GT_GUI_MultiblocksHandler extends GUI_MultiblocksHandler<IConstructable> {

    protected static final int TIER_BUTTON_X = LAYER_BUTTON_X + 5;
    protected static final int TIER_BUTTON_Y = LAYER_BUTTON_Y - ICON_SIZE_Y;
    protected static final int TIER_BUTTON_SPACE_X = 25;
    protected static final int PROJECT_BUTTON_X = 145;
    protected static final int PROJECT_BUTTON_Y = -5;
    private static final BlockPosition MB_PLACE_POS = new BlockPosition(0, 64, 0);
    public static final int MAX_PLACE_ROUNDS = 2000;

    protected static int tierIndex = 1;

    private static EntityPlayer fakeMultiblockBuilder;

    private final List<List<ItemStack>> candidates = new ArrayList<>();
    protected List<PositionedIStructureElement> structureElements = new ArrayList<>();
    protected Consumer<List<List<ItemStack>>> onCandidateChanged;

    public GT_GUI_MultiblocksHandler() {
        super();

        ClearGuiButton previousTierButton = new ClearGuiButton(
                0,
                TIER_BUTTON_X,
                TIER_BUTTON_Y,
                ICON_SIZE_X,
                ICON_SIZE_Y,
                "<");
        ClearGuiButton nextTierButton = new ClearGuiButton(
                0,
                TIER_BUTTON_X + ICON_SIZE_X + TIER_BUTTON_SPACE_X,
                TIER_BUTTON_Y,
                ICON_SIZE_X,
                ICON_SIZE_Y,
                ">");
        GuiButton projectMultiblocksButton = new GuiButton(
                0,
                PROJECT_BUTTON_X,
                PROJECT_BUTTON_Y,
                ICON_SIZE_X,
                ICON_SIZE_Y,
                "P");

        buttons.put(previousTierButton, this::togglePreviousTier);
        buttons.put(nextTierButton, this::toggleNextTier);
        buttons.put(projectMultiblocksButton, this::projectMultiblocks);
    }

    public void setOnCandidateChanged(Consumer<List<List<ItemStack>>> callback) {
        onCandidateChanged = callback;
    }

    @Override
    protected void loadNewMultiblock() {
        tierIndex = 1;
        layerIndex = -1;
        initializeSceneRenderer(true);
        lastRenderingController = renderingController;
    }

    private void projectMultiblocks() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        World baseWorld = Minecraft.getMinecraft().theWorld;
        MovingObjectPosition lookingPos = player.rayTrace(10, 1);
        if (lookingPos.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) return;
        int playerDir = MathHelper.floor_double((player.rotationYaw * 4F) / 360F + 0.5D) & 3;
        ItemStack itemStack = stackForm.copy();
        if (!baseWorld.isAirBlock(lookingPos.blockX, lookingPos.blockY + 1, lookingPos.blockZ)) return;
        itemStack.getItem().onItemUse(
                itemStack,
                player,
                baseWorld,
                lookingPos.blockX,
                lookingPos.blockY + 1,
                lookingPos.blockZ,
                0,
                lookingPos.blockX,
                lookingPos.blockY,
                lookingPos.blockZ);
        ConstructableUtility.handle(
                getTriggerStack(),
                player,
                baseWorld,
                lookingPos.blockX,
                lookingPos.blockY + 1,
                lookingPos.blockZ,
                playerDir);
        baseWorld.setBlockToAir(lookingPos.blockX, lookingPos.blockY + 1, lookingPos.blockZ);
        baseWorld.removeTileEntity(lookingPos.blockX, lookingPos.blockY + 1, lookingPos.blockZ);
    }

    @NotNull
    private static ItemStack getTriggerStack() {
        return new ItemStack(StructureLibAPI.getDefaultHologramItem(), tierIndex);
    }

    private void toggleNextTier() {
        tierIndex++;
        initializeSceneRenderer(false);
    }

    private void togglePreviousTier() {
        if (tierIndex > 1) {
            tierIndex--;
            initializeSceneRenderer(false);
        }
    }

    @Override
    protected String getMultiblockName() {
        return I18n.format(stackForm.getDisplayName());
    }

    @Override
    protected void drawButtonsTitle() {
        super.drawButtonsTitle();

        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        String tierText = "Tier: " + tierIndex;
        fontRenderer.drawString(
                tierText,
                TIER_BUTTON_X + ICON_SIZE_X + (TIER_BUTTON_SPACE_X - fontRenderer.getStringWidth(tierText)) / 2,
                TIER_BUTTON_Y + 5,
                0x333333);
    }

    @Override
    protected void placeMultiblock() {
        if (GT_Runnable_MachineBlockUpdate.isCurrentThreadEnabled())
            GT_Runnable_MachineBlockUpdate.setCurrentThreadEnabled(false);

        fakeMultiblockBuilder = new ClientFakePlayer(
                renderer.world,
                new GameProfile(UUID.fromString("518FDF18-EC2A-4322-832A-58ED1721309B"), "[GregTech]"));
        renderer.world.unloadEntities(Arrays.asList(fakeMultiblockBuilder));

        IConstructable constructable = null;

        int oStackSize = stackForm.stackSize;
        stackForm.getItem().onItemUse(
                stackForm,
                fakeMultiblockBuilder,
                renderer.world,
                MB_PLACE_POS.x,
                MB_PLACE_POS.y,
                MB_PLACE_POS.z,
                0,
                MB_PLACE_POS.x,
                MB_PLACE_POS.y,
                MB_PLACE_POS.z);
        stackForm.stackSize = oStackSize;

        TileEntity tTileEntity = renderer.world.getTileEntity(MB_PLACE_POS.x, MB_PLACE_POS.y, MB_PLACE_POS.z);
        ((ITurnable) tTileEntity).setFrontFacing(ForgeDirection.SOUTH);
        IMetaTileEntity mte = ((IGregTechTileEntity) tTileEntity).getMetaTileEntity();

        if (!StructureLibAPI.isInstrumentEnabled()) StructureLibAPI.enableInstrument(BlockRenderer6343.MOD_ID);
        structureElements.clear();

        if (mte instanceof ISurvivalConstructable) {
            int result, iterations = 0;
            do {
                result = ((ISurvivalConstructable) mte).survivalConstruct(
                        getTriggerStack(),
                        Integer.MAX_VALUE,
                        ISurvivalBuildEnvironment.create(CreativeItemSource.instance, fakeMultiblockBuilder));
                iterations++;
            } while (result > 0 && iterations < MAX_PLACE_ROUNDS);
        } else if (tTileEntity instanceof IConstructableProvider) {
            constructable = ((IConstructableProvider) tTileEntity).getConstructable();
        } else if (tTileEntity instanceof IConstructable) {
            constructable = (IConstructable) tTileEntity;
        }
        if (constructable != null) {
            constructable.construct(getTriggerStack(), false);
        }

        if (StructureLibAPI.isInstrumentEnabled()) StructureLibAPI.disableInstrument();

        if (!GT_Runnable_MachineBlockUpdate.isCurrentThreadEnabled())
            GT_Runnable_MachineBlockUpdate.setCurrentThreadEnabled(true);
    }

    private void scanCandidates() {
        candidates.clear();
        if (selectedBlock != null) {
            for (PositionedIStructureElement structureElement : structureElements) {
                if (structureElement.x == selectedBlock.x && structureElement.y == selectedBlock.y
                        && structureElement.z == selectedBlock.z) {

                    IStructureElement.BlocksToPlace blocksToPlace = structureElement.element.getBlocksToPlace(
                            renderingController,
                            renderer.world,
                            selectedBlock.x,
                            selectedBlock.y,
                            selectedBlock.z,
                            getTriggerStack(),
                            AutoPlaceEnvironment.fromLegacy(
                                    CreativeItemSource.instance,
                                    fakeMultiblockBuilder,
                                    iChatComponent -> {}));
                    if (blocksToPlace != null) {
                        Predicate<ItemStack> predicate = blocksToPlace.getPredicate();
                        Set<ItemStack> rawCandidates = CreativeItemSource.instance
                                .takeEverythingMatches(predicate, false, 0).keySet();

                        List<List<ItemStack>> stackedCandidates = new ArrayList<>();
                        for (ItemStack rawCandidate : rawCandidates) {
                            boolean added = false;
                            for (List<ItemStack> stackedCandidate : stackedCandidates) {
                                List<String> firstCandidateTooltip = stackedCandidate.get(0)
                                        .getTooltip(fakeMultiblockBuilder, false);
                                List<String> rawCandidateTooltip = rawCandidate
                                        .getTooltip(fakeMultiblockBuilder, false);
                                if (firstCandidateTooltip.size() > 1 && rawCandidateTooltip.size() > 1
                                        && firstCandidateTooltip.get(1).equals(rawCandidateTooltip.get(1))) {
                                    stackedCandidate.add(rawCandidate);
                                    added = true;
                                    break;
                                }
                            }
                            if (!added) {
                                List<ItemStack> newStackedCandidate = new ArrayList<>();
                                newStackedCandidate.add(rawCandidate);
                                stackedCandidates.add(newStackedCandidate);
                            }
                        }

                        candidates.addAll(stackedCandidates);

                        if (onCandidateChanged != null) {
                            onCandidateChanged.accept(candidates);
                        }
                    }
                    return;
                }
            }
        }
        if (onCandidateChanged != null) {
            onCandidateChanged.accept(candidates);
        }
    }

    @Override
    protected void onBlockSelected() {
        scanCandidates();
    }

    @SubscribeEvent
    public void OnStructureEvent(StructureEvent.StructureElementVisitedEvent event) {
        structureElements.add(
                new PositionedIStructureElement(
                        event.getX(),
                        event.getY(),
                        event.getZ(),
                        (IStructureElement<IConstructable>) event.getElement()));
    }
}
