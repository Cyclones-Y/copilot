import { useFileStore } from "@/components/WeIde/stores/fileStore";
import { FolderTree, Settings, Upload, RefreshCw } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useState } from 'react';
import { Tooltip, message } from 'antd';
import { FileUploadArea } from './FileUploadArea';
import { ProjectRootSelector } from './ProjectRootSelector';
import { refreshIndex, getWorkspacePath } from '@/api/knowledge';

export function Header() {
  const { setFiles, setIsFirstSend, setIsUpdateSend, projectRoot } = useFileStore();
  const { t } = useTranslation();
  const [showUploadArea, setShowUploadArea] = useState(false);
  const [showRootSelector, setShowRootSelector] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const handleClearAll = () => {
    setFiles({});
    setIsFirstSend();
    setIsUpdateSend();
  };

  const handleUploadClick = () => {
    setShowUploadArea(true);
  };

  const handleSettingsClick = () => {
    setShowRootSelector(true);
  };

  const handleRefreshIndex = async () => {
    if (isRefreshing) return;

    setIsRefreshing(true);
    const hide = message.loading('正在刷新知识库索引...', 0);

    try {
      // 从后端获取 workspace 的绝对路径
      console.log('获取 workspace 路径...');
      const workspacePath = await getWorkspacePath();
      console.log('刷新知识库索引, path:', workspacePath);

      await refreshIndex(workspacePath);

      hide();
      message.success('索引刷新完成');
    } catch (error) {
      hide();
      console.error('刷新索引失败:', error);
      message.error('刷新索引失败: ' + (error instanceof Error ? error.message : String(error)));
    } finally {
      setIsRefreshing(false);
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between">
        <h2 className="text-[13px] uppercase font-semibold mb-2 flex items-center text-[#424242] dark:text-gray-400 select-none">
          <FolderTree className="w-4 h-4 mr-1.5" /> {t("explorer.explorer")}
        </h2>
        <div className="flex items-center mb-2 space-x-2">
          <Settings
            title={t("explorer.project_settings")}
            className="w-4 h-4 text-[#616161] dark:text-gray-400 cursor-pointer hover:text-[#333] dark:hover:text-gray-300"
            onClick={handleSettingsClick}
          />
          <Tooltip title="刷新知识库索引">
            <RefreshCw
              className={`w-4 h-4 text-[#616161] dark:text-gray-400 cursor-pointer hover:text-[#333] dark:hover:text-gray-300 ${isRefreshing ? 'animate-spin' : ''
                }`}
              onClick={handleRefreshIndex}
            />
          </Tooltip>
          <Upload
            title={t("explorer.upload_file")}
            className="w-4 h-4 text-[#616161] dark:text-gray-400 cursor-pointer hover:text-[#333] dark:hover:text-gray-300"
            onClick={handleUploadClick}
          />
          <span
            onClick={handleClearAll}
            className="text-[10px] text-[#616161] dark:text-gray-400 cursor-pointer hover:text-[#333] dark:hover:text-gray-300"
          >
            {t("explorer.clear_all")}
          </span>
        </div>
      </div>

      <FileUploadArea
        isOpen={showUploadArea}
        onClose={() => setShowUploadArea(false)}
      />

      <ProjectRootSelector
        isOpen={showRootSelector}
        onClose={() => setShowRootSelector(false)}
      />
    </div>
  );
}
