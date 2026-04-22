local GameConfig = require("Game.Core.Util.GameConfigWrap")
local LuaDynamicDelegateClass = require("Common.Framework.Event.LuaDynamicDelegate")
local ClientData = require("Game.Mod.BaseMod.Client.ClientData")
local GameBlackboard = require("Game.Core.Util.GameDataBlackboard")

local GameAPI = GameAPI

---@class MainCity2DJengaClientSystem : CModuleBase
---@field __super CModuleBase
local MainCity2DJengaClientSystem = {}

function MainCity2DJengaClientSystem:ctor()
    ---是否初始化完毕
    self.bInitJengaDataFinished = false
end

function MainCity2DJengaClientSystem:Initialize()
    MainCity2DJengaClientSystem.__super.Initialize(self)
end

function MainCity2DJengaClientSystem:Destroy()
    MainCity2DJengaClientSystem.__super.Destroy(self)
end

function MainCity2DJengaClientSystem:GetLocalJengaList()
    local LocalPlayerKey = ClientData.LocalPlayerKey
    local JengaId = self.PlayerJengaData[LocalPlayerKey]
    if not JengaId then
        return {}
    end
    
    local JengaList = self.JengaList[JengaId]
    return JengaList or {}
end

---@private
function MainCity2DJengaClientSystem:StartJenga(PlayerKey, JengaId, TargetPlayerKey)
    self:InsertJengaData(PlayerKey, JengaId, TargetPlayerKey)
end

---@private
function MainCity2DJengaClientSystem:InsertJengaData(PlayerKey, JengaId, TargetPlayerKey)
    local PlayerJengaData = self.PlayerJengaData
    local JengaList = self.JengaList
    PlayerJengaData[PlayerKey] = JengaId
    if not PlayerJengaData[TargetPlayerKey] then
        PlayerJengaData[TargetPlayerKey] = JengaId
    end
    
    local JengaListPlayers = JengaList[JengaId]
    if not JengaListPlayers then
        JengaListPlayers = { TargetPlayerKey }
        JengaList[JengaId] = JengaListPlayers
    end
    
    JengaListPlayers[#JengaListPlayers + 1] = PlayerKey
end

local ModuleBase = require("Common.Framework.Module.ModuleBase")
local Class = require("Common.Class")
return Class(ModuleBase, nil, MainCity2DJengaClientSystem)